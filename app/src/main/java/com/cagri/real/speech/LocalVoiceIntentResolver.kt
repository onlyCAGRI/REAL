package com.cagri.real.speech

import com.cagri.real.data.CustomCategory
import com.cagri.real.data.RecurrenceRule
import com.cagri.real.data.ReminderItem
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Safe local front-end returning REAL's canonical VoiceParseResult.
 * Null delegates the WHOLE utterance to Groq; never applies a partial interpretation.
 * It does not persist data, request consent, run a network call or schedule an alarm.
 */
object LocalVoiceIntentResolver {
    fun resolve(
        transcript:String, cards:List<ReminderItem>, categories:List<CustomCategory>,
        lastCard:ReminderItem?, now:ZonedDateTime,
        alternatives:List<String> = emptyList(), timeFollowUp:Boolean=false
    ): GeminiVoiceParseResult? {
        val raw=stripBoundaryFillerYa(transcript).trim().trimEnd('.','!','?')
        if(raw.isBlank() || raw.length>2000) return null
        val parsed=resolveOne(raw,cards,categories,lastCard,now,timeFollowUp) ?: return null
        // Different STT spellings are allowed only when they produce the same complete operation.
        if(alternatives.filter { it.isNotBlank() }.any {
            resolveOne(stripBoundaryFillerYa(it).trim().trimEnd('.','!','?'),cards,categories,lastCard,now,timeFollowUp)!=parsed
        }) return null
        return parsed
    }

    private fun resolveOne(raw:String,cards:List<ReminderItem>,categories:List<CustomCategory>,lastCard:ReminderItem?,now:ZonedDateTime,timeFollowUp:Boolean):GeminiVoiceParseResult? {
        if(timeFollowUp) return clockReply(raw,now)
        val key=localVoiceKey(raw)
        if(Regex("\\b(silme|tamamlama|erteleme|degil|belki|galiba|acaba|eger|olursa|yoksa|istersen|sanirim|istemiyorum|ornegin|mesela|vazgec|iptal)\\b").containsMatchIn(key)) return null
        bulk(raw,now)?.let { return it }
        categoryCreation(raw,categories,now)?.let { return it }
        action(raw,cards,categories,lastCard,now)?.let { return it }
        categoryNote(raw,categories,now)?.let { return it }
        // Commands and corrections that weren't conclusively resolved must never become echo cards.
        if(Regex("\\b(sil|silinsin|tamamla|tamamlandi|bitti|ertele|tasi|degistir|hayir|geri al|atla|tekrarla|tekrarlasin|tekrarlansin|kaldir|iptal|kategori)\\b").containsMatchIn(key)) {
            return categoryNote(raw,categories,now)
        }
        return create(raw,now)
    }

    private fun result(action:VoiceAction, edit:GeminiVoiceEdit?=null) = GeminiVoiceParseResult(emptyList(),edit=edit,action=action)
    private fun clockReply(raw:String,now:ZonedDateTime):GeminiVoiceParseResult? {
        val key=localVoiceKey(raw)
        if(!Regex("(?:(?:sabah|aksam|ogle|oglen|gece)\\s+)?(?:saat\\s+)?(?:\\d{1,2}(?:[:.]\\d{2})?|[a-z]+(?:\\s+[a-z]+)?)(?:[' ]?(?:da|de|ta|te))?").matches(key)) return null
        val marked=if(key.contains("saat") || key.contains(Regex("[:.]"))) raw else {
            val prefix=Regex("^(sabah|aksam|ogle|oglen|gece)\\s+").find(key)
            if(prefix!=null) raw.substring(0,prefix.range.last+1)+"saat "+raw.substring(prefix.range.last+1) else "saat $raw"
        }
        val timing=LocalVoiceScheduleParser.parse(marked,now) ?: return null
        if(timing.remaining.isNotBlank() || !timing.consumed) return null
        return GeminiVoiceParseResult(listOf(intent("Saat yanıtı",timing)))
    }

    private fun action(raw:String,cards:List<ReminderItem>,categories:List<CustomCategory>,last:ReminderItem?,now:ZonedDateTime):GeminiVoiceParseResult? {
        val key=localVoiceKey(raw)
        val direct=Regex("^(.*?)(?:\\s+)?(tamamlandi|tamamla|bitti|silinsin|sil|tamamlamayi geri al|tamamlamayi geri alip ac|yeniden ac|bu seferlik atla|atla)$").matchEntire(key)
        if(direct!=null && !Regex("\\b(?:konumu|konumunu|tekrari|tekrarini)\\b").containsMatchIn(direct.groupValues[1])) {
            val verb=direct.groupValues[2]
            val target=LocalVoiceTargets.find(direct.groupValues[1],cards,last) ?: return null
            val type=when(verb) {
                "sil","silinsin"->VoiceActionType.DELETE
                "atla","bu seferlik atla"->VoiceActionType.SKIP_OCCURRENCE
                "tamamlamayi geri al","tamamlamayi geri alip ac","yeniden ac"->VoiceActionType.REOPEN
                else->VoiceActionType.COMPLETE
            }
            return result(VoiceAction(type,target.id))
        }
        val addition=Regex("^(.+?)\\s+(?:listesine|listeme|listeme de|listesi icine)\\s+(.+?)\\s+(?:ekle|koy)$").matchEntire(key)
        if(addition!=null) {
            val target=LocalVoiceTargets.find(addition.groupValues[1],cards.filter { it.isChecklist },last?.takeIf { it.isChecklist }) ?: return null
            val content=raw.substring(addition.groups[2]!!.range)
            val items=splitChecklistItems(content)
            if(items.isEmpty() || items.size>30) return null
            return result(VoiceAction(VoiceActionType.ADD_CHECKLIST_ITEMS,target.id,checklistItems=items))
        }
        val rename=Regex("^(.+?)\\s+(?:kartinin\\s+)?(?:adini|basligini)\\s+(.+?)\\s+(?:yap|olarak degistir)$").matchEntire(key)
        if(rename!=null) {
            val target=LocalVoiceTargets.find(rename.groupValues[1],cards,last) ?: return null
            return result(VoiceAction(VoiceActionType.EDIT,target.id),VoiceEditPatch(newTitle=title(raw.substring(rename.groups[2]!!.range))))
        }
        val correction=Regex("^(?:ay\\s+)?hayir\\s+(.+)$").matchEntire(key)
        if(correction!=null && last!=null) {
            val replacement=raw.substring(correction.groups[1]!!.range)
            // Simple object correction retains the original verb; complex sentence corrections go to Groq.
            val oldWords=last.text.trim().split(Regex("\\s+"))
            val replacementKey=localVoiceKey(replacement)
            if(!Regex("\\s").containsMatchIn(replacement) && oldWords.size==2 &&
                Regex("[a-z]{3,}[iu]").matches(replacementKey) && localNumber(replacementKey)==null &&
                LocalVoiceScheduleParser.parse(replacement,now)?.consumed==false &&
                localVoiceKey(oldWords.last()) in setOf("ara","al","oku","yaz")) {
                val verb=oldWords.last()
                return result(VoiceAction(VoiceActionType.EDIT,last.id),VoiceEditPatch(newTitle=title("$replacement $verb")))
            }
        }
        val move=Regex("^(.+?)\\s+(.+?)\\s+(?:kategorisine|kategorisine de)\\s+(?:tasi|al|koy)$").matchEntire(key)
        if(move!=null) {
            // Category names may contain spaces: parse by matching a unique real category suffix.
            val matches=categories.mapNotNull { cat ->
                val suffix=localVoiceKey(cat.name)+" kategorisine"
                val at=key.lastIndexOf(suffix)
                if(at<=0 || !key.substring(at).matches(Regex(Regex.escape(suffix)+" (?:tasi|al|koy)"))) null
                else LocalVoiceTargets.find(key.substring(0,at).trim(),cards,last)?.let { it to cat }
            }
            val (target,cat)=matches.singleOrNull() ?: return null
            return result(VoiceAction(VoiceActionType.EDIT,target.id),VoiceEditPatch(categoryName=cat.name))
        }
        val clear=Regex("^(.*?)\\s*(tekrari|tekrarini|konumu|konumunu)\\s+(?:kaldir|sil)$").matchEntire(key)
        if(clear!=null) {
            val target=LocalVoiceTargets.find(clear.groupValues[1],cards,last) ?: return null
            val repeat=clear.groupValues[2].startsWith("tekrar")
            return result(VoiceAction(VoiceActionType.EDIT,target.id),VoiceEditPatch(clearRepeat=repeat,clearLocation=!repeat))
        }
        val loc=Regex("^(.*?)\\s*(?:konumu|konumunu)\\s+(.+?)\\s+(?:yap|olarak ayarla)$").matchEntire(key)
        if(loc!=null) {
            val target=LocalVoiceTargets.find(loc.groupValues[1],cards,last) ?: return null
            return result(VoiceAction(VoiceActionType.EDIT,target.id),VoiceEditPatch(locationName=raw.substring(loc.groups[2]!!.range)))
        }
        val relative=Regex("^(.*?)\\s*(\\d{1,3}|bir|iki|uc|dort|bes|alti|yedi|sekiz|dokuz|on)\\s+(dakika|saat|gun|hafta)\\s+(?:ertele|ileri al)$").matchEntire(key)
        if(relative!=null) {
            val target=LocalVoiceTargets.find(relative.groupValues[1],cards,last) ?: return null
            val amount=localNumber(relative.groupValues[2]) ?: return null
            if(amount !in 1..366) return null
            val old=target.alertAt?.let { Instant.ofEpochMilli(it).atZone(now.zone) }
            val unit=relative.groupValues[3]
            if(old==null) {
                if(unit !in setOf("gun","hafta")) return null
                val day=target.plannedDateEpochDay?.let { LocalDate.ofEpochDay(it) } ?: return null
                return result(VoiceAction(VoiceActionType.POSTPONE,target.id),VoiceEditPatch(date=day.plusDays(amount.toLong()*(if(unit=="hafta") 7 else 1))))
            }
            val shifted=when(unit) { "dakika"->old.plusMinutes(amount.toLong());"saat"->old.plusHours(amount.toLong());"gun"->old.plusDays(amount.toLong());else->old.plusWeeks(amount.toLong()) }
            return result(VoiceAction(VoiceActionType.POSTPONE,target.id),VoiceEditPatch(date=shifted.toLocalDate(),time=shifted.toLocalTime()))
        }
        // Schedule-only follow-ups or named-card edits. Preserve unsaid fields via VoiceEditResolver.
        val ending=Regex("\\s+(?:al|ertele|tasi|ayarla|yap|tekrarlasin|tekrarlansin|tekrar etsin)$").find(key)
        val follow=key.startsWith("hayir ") || key.startsWith("ay hayir ")
        if(ending!=null || follow) {
            var body=if(ending!=null) raw.take(ending.range.first) else raw
            body=body.replace(Regex("(?i)^(?:ay\\s+)?hay[ıi]r\\s+"),"")
            body=body.replace(Regex("(?i)yar[ıi]na\\b"),"yarın").replace(Regex("(?i)bug[üu]ne\\b"),"bugün")
                .replace(Regex("(?i)haftaya\\b"),"haftaya")
            val timing=LocalVoiceScheduleParser.parse(body,now) ?: return null
            if(!timing.consumed || timing.ambiguousClock) return null
            val target=LocalVoiceTargets.find(timing.remaining,cards,last) ?: return null
            val edit=VoiceEditPatch<LocalDate,LocalTime>(date=timing.date.takeIf { timing.dateSpecified && timing.weekdays==0 },time=timing.time,
                recurrence=timing.rule.takeUnless { it==RecurrenceRule.NONE },weekdaysMask=timing.weekdays,repeatUntil=timing.until)
            // A daypart without a clock cannot silently retain an unrelated existing hour.
            if(timing.daypart!=null && timing.time==null) return null
            return result(VoiceAction(VoiceActionType.EDIT,target.id),edit)
        }
        return null
    }

    private fun bulk(raw:String,now:ZonedDateTime):GeminiVoiceParseResult? {
        val key=localVoiceKey(raw)
        val match=Regex("^(bugunkulerin|bugunkuleri|yarinkilerin|yarinkileri|gecikenlerin|gecikenleri)(?:\\s+(?:hepsini|tumunu))?\\s+(tamamla|sil|yarina (?:tasi|ertele|at)|bugune (?:tasi|al))$").matchEntire(key) ?: return null
        val scope=when { match.groupValues[1].startsWith("bugun")->VoiceBulkScope.TODAY;match.groupValues[1].startsWith("yarin")->VoiceBulkScope.TOMORROW;else->VoiceBulkScope.OVERDUE }
        val verb=match.groupValues[2]
        val op=when(verb) { "tamamla"->VoiceBulkOperation.COMPLETE;"sil"->VoiceBulkOperation.DELETE;else->VoiceBulkOperation.SHIFT }
        val date=if(op==VoiceBulkOperation.SHIFT) now.toLocalDate().plusDays(if(verb.startsWith("yarina")) 1 else 0).toString() else ""
        return result(VoiceAction(VoiceActionType.BULK,bulk=VoiceBulk(op,scope,newDate=date)))
    }

    private fun categoryCreation(raw:String,categories:List<CustomCategory>,now:ZonedDateTime):GeminiVoiceParseResult? {
        val key=localVoiceKey(raw)
        val m=Regex("^(?:yeni\\s+)?(.+?)\\s+(?:kategorisi|kategorisini|kategorisi olarak)\\s+(?:ac|olustur)(?:\\s+icine\\s+(.+?)\\s+(?:koy|ekle))?$").matchEntire(key) ?: return null
        val name=title(raw.substring(m.groups[1]!!.range));if(name.length>80) return null
        val existing=findVoiceCategory(name,categories)
        // Duplicate normalized names require the AI/user, not an arbitrary category choice.
        if(categories.count { voiceNameKey(it.name)==voiceNameKey(name) }>1) return null
        val items=if(m.groupValues[2].isBlank()) emptyList() else {
            val goals=raw.substring(m.groups[2]!!.range).split(Regex("[,;]+|\\s+ve\\s+",RegexOption.IGNORE_CASE)).map { it.trim() }
            if(goals.any { it.isBlank() } || goals.size>20) return null
            goals.map { goal ->
                val one=create("not al: $goal",now)?.intents?.singleOrNull() ?: return null
                one.copy(categoryName=existing?.name ?: name)
            }
        }
        if(items.size>1 && items.any { it.requiresTimeSelection }) return null
        return GeminiVoiceParseResult(items,newCategory=if(existing==null) VoiceNewCategoryRequest(name,"BOOKMARK",null) else null)
    }

    private fun categoryNote(raw:String,categories:List<CustomCategory>,now:ZonedDateTime):GeminiVoiceParseResult? {
        val key=localVoiceKey(raw)
        val matches=categories.mapNotNull { cat ->
            val start=localVoiceKey(cat.name)+" kategorisine "
            if(!key.startsWith(start)) null else {
                val suffix=Regex("\\s+(?:koy|ekle|kaydet)$").find(key) ?: return@mapNotNull null
                if(suffix.range.first<=start.length) return@mapNotNull null
                val note=raw.substring(start.length,suffix.range.first)
                create("not al: $note",now)?.let { parsed -> parsed.copy(intents=parsed.intents.map { it.copy(categoryName=cat.name) }) }
            }
        }
        return matches.singleOrNull()
    }

    private fun create(raw:String,now:ZonedDateTime):GeminiVoiceParseResult? {
        val key=localVoiceKey(raw)
        if(Regex("\\b(silme|tamamlama|erteleme|degil|belki|galiba|acaba|eger|olursa|yoksa|istersen|sanirim|istemiyorum|ornegin|mesela)\\b").containsMatchIn(key)) return null
        // Creative/catalog reasoning and conditional prose stay with Groq.
        if(Regex("\\b(oneri|oner|onersene|film|dizi|muzik|sarki|izle|izleyecegim|izlicem|konum|kategori|hayir|sil|tamamla|ertele|degistir|atla|tekrar)\\b").containsMatchIn(key)) return null
        // Explicit clause boundaries only. No splitting conjunctions in objects/checklist rows.
        val clauses=raw.split(Regex("\\s+(?:ayrica|bir de|sonra da)\\s+|[;]+",RegexOption.IGNORE_CASE))
        if(clauses.size>1) {
            if(clauses.size>20) return null
            val intents=clauses.map { create(it,now)?.intents?.singleOrNull() ?: return null }
            // Date inheritance across clauses and multiple clock questions are semantic AI work.
            if(intents.any { it.requiresTimeSelection } || intents.map { it.hasSchedule }.distinct().size>1) return null
            return GeminiVoiceParseResult(intents)
        }
        val timing=LocalVoiceScheduleParser.parse(raw,now) ?: return null
        var task=timing.remaining.replace(Regex("(?i)\\s+(?:bana\\s+)?hat[ıi]rlat$"),"").trim()
        if(task.isBlank()) return null
        val checklist=extractChecklist(task)
        if(checklist!=null) {
            if(checklist.items.isEmpty() || checklist.items.size>30) return null
            return GeminiVoiceParseResult(listOf(intent(checklist.title,timing).copy(isChecklist=true,checklistItems=checklist.items)))
        }
        // A coordinated sentence may contain two tasks; never fold it into one local card.
        if(Regex("\\b(ve|sonra|ardindan|ama|fakat|ile)\\b").containsMatchIn(localVoiceKey(task))) return null
        val explicitNote=Regex("(?i)^(?:not\\s+(?:al|ekle)|kart\\s+ekle)[: ]+(.+)$").matchEntire(task)
        if(explicitNote!=null) task=explicitNote.groupValues[1]
        else {
            val last=localVoiceKey(task.substringAfterLast(' '))
            val verbs=setOf("ara","aramam","arayacagim","arayacam","aricam","git","gidecegim","gidicem","gitcem","gidecem","yap","yapacagim","yapicam","al","alacagim","alicam","alcam","oku","okuyacagim","okucam","ode","odeyecegim","odiycem","calis","calisacagim","calisicam","ic","icecegim","icicem","yaz","yazacagim","yazicam","temizle","temizleyecegim","gonder","gonderecegim","gondericem","kos","kosacagim","kosucam","uyu","uyuyacagim","uyucam","bitir","bitirecegim","bitiricem","yapmam","gitmem")
            if(last !in verbs) {
                // Explicit category goal/not commands may be nouns, but arbitrary prose is not assumed a task.
                if(explicitNote==null) return null
            }
            val mapped=mapOf("gitcem" to "git","gidicem" to "git","alcam" to "al","okucam" to "oku","okuyacagim" to "oku","yazacagim" to "yaz","yazicam" to "yaz","kosacagim" to "koş","kosucam" to "koş","uyuyacagim" to "uyu","uyucam" to "uyu","bitirecegim" to "bitir","bitiricem" to "bitir","calisacagim" to "çalış","calisicam" to "çalış","aramam" to "ara","gitmem" to "git","yapmam" to "yap")
            val word=task.substringAfterLast(' ');val normalized=mapped[last] ?: normalizeVerb(word)
            task=if(' ' in task) task.substringBeforeLast(' ')+" "+normalized else normalized
        }
        return GeminiVoiceParseResult(listOf(intent(title(task),timing)))
    }

    private fun intent(title:String,t:LocalVoiceTiming)=VoiceIntent(
        title=title,date=t.date,time=t.time,recurrence=t.rule,hasSchedule=t.consumed,
        requiresTimeSelection=t.needsClock,timeOfDay=t.daypart,durationMinutes=null,people=emptyList(),
        locationName=null,categoryName=null,isChecklist=false,checklistItems=emptyList(),weekdaysMask=t.weekdays,repeatUntil=t.until)
    private fun title(text:String)=text.trim().replaceFirstChar { if(it.isLowerCase()) it.titlecase(Locale.forLanguageTag("tr-TR")) else it.toString() }
}
