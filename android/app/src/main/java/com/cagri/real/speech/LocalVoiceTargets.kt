package com.cagri.real.speech

import com.cagri.real.data.ReminderItem

/** Exact or unique token matches only. Ranking alone must never authorize a mutation. */
internal object LocalVoiceTargets {
    private fun words(text: String) = localVoiceKey(text).split(Regex("[^a-z0-9]+"))
        .filter { it.isNotBlank() }
    private fun related(a:String,b:String):Boolean {
        if(a==b) return true
        val endings=listOf("ini","unu","imi","umu","in","un","im","um","i","u","a","e")
        val rootsA=endings.filter { a.endsWith(it) && a.length-it.length>=4 }.map { a.dropLast(it.length) }
        val rootsB=endings.filter { b.endsWith(it) && b.length-it.length>=4 }.map { b.dropLast(it.length) }
        if(rootsA.any { it in rootsB }) return true
        return endings.any { suffix ->
            (a.length-suffix.length>=4 && a.endsWith(suffix) && a.dropLast(suffix.length)==b) ||
            (b.length-suffix.length>=4 && b.endsWith(suffix) && b.dropLast(suffix.length)==a)
        }
    }
    fun find(query:String,cards:List<ReminderItem>,last:ReminderItem?):ReminderItem? {
        val clean=localVoiceKey(query).replace(Regex("\\b(?:kartini|karti|kart|gorevini|gorevi|gorev)\\b")," ").trim()
        if(clean in setOf("","onu","bunu","bu","o","son kart","son ekledigim")) return last
        val wanted=words(clean)
        if(wanted.isEmpty() || wanted.any { it.length<3 }) return null
        val exact=cards.filter { words(it.text)==wanted }
        if(exact.size==1) return exact.single()
        if(exact.size>1) return null
        return cards.filter { card ->
            val title=words(card.text)
            wanted.all { q->title.any { related(q,it) } }
        }.singleOrNull()
    }
}
