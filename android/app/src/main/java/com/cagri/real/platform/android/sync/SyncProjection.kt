package com.cagri.real.platform.android.sync

import org.json.JSONArray
import org.json.JSONObject

/** Lossless adapter for the existing persisted product model. Identity fields stay stable across devices. */
internal object SyncProjection {
    const val CARDS="big_bos_cagri_reminders"
    const val CATEGORIES="big_bos_cagri_categories"
    const val MOODS="real_mood_tracker"
    const val PLANNER="real_planner_v1"
    const val WATER="real_water_tracker_v1"
    const val DESCRIPTIONS="real_category_descriptions"
    const val UI="real_ui"
    val localFiles=listOf("real_pending_content","real_pending_location")
    val files=listOf(CARDS,CATEGORIES,MOODS,PLANNER,WATER,DESCRIPTIONS,UI)+localFiles
    val uiKeys=setOf("category_order_v1","sort_order","category_sort_order","home_category_preview_count_v1","planner_card_color_index_v1")
    fun tracked(file:String,key:String):Boolean=when(file) {
        CARDS->key=="reminders";CATEGORIES->key in setOf("categories_json","builtin_category_colors")
        MOODS->key=="entries_v1";PLANNER->key=="event_details";WATER->key=="water_tracker"
        DESCRIPTIONS->key.startsWith("custom_") || key.startsWith("builtin_")
        UI->key in uiKeys;else->file in localFiles
    }
    data class Document(val kind:String,val key:String,val payload:String)
    /** The local file owns exactly this record partition, including its tombstones. */
    fun ownsDocument(file:String,kind:String,key:String):Boolean = when(file) {
        CARDS -> kind == "CARD"
        CATEGORIES -> kind == "CATEGORY" || (kind == "PREFERENCE" && key.startsWith("colors/"))
        MOODS -> kind == "MOOD"
        PLANNER -> kind == "PLANNER"
        WATER -> kind == "DRINK" || (kind == "PREFERENCE" && key == "water/goal")
        DESCRIPTIONS -> kind == "PREFERENCE" && key.startsWith("description/")
        UI -> kind == "PREFERENCE" && key.startsWith("ui/")
        else -> false
    }
    fun export(db:SyncDatabase,scope:String,file:String? = null):List<Document> {
        val result=mutableListOf<Document>()
        fun raw(source:String,key:String)=if(file == null || file == source) db.value(scope,source,key) as String? else null
        fun rows(file:String,key:String)=raw(file,key)?.let(::JSONArray)?.objects().orEmpty()
        fun add(kind:String,key:String,obj:JSONObject) { result+=Document(kind,key,canonical(obj)) }
        for(card in rows(CARDS,"reminders")) {
            // Ortak kategori cards travel through the shared category store, never personal sync.
            if(card.has("sharedCardId")) continue
            card.remove("notifiedAlertAt")
            transformBinaries(db,scope,card,true)
            add("CARD",card.getLong("id").toString(),card)
        }
        rows(CATEGORIES,"categories_json").forEach {
            // Someone else's shared category is a mirror on this phone; the owner's own one stays (minus the bookkeeping).
            val role=it.optString("sharedRole","")
            if(it.has("sharedId") && role!="owner") return@forEach
            it.remove("sharedId");it.remove("sharedRole");it.remove("sharedOwnerName");it.remove("sharedDescription")
            add("CATEGORY",it.getLong("id").toString(),it)
        }
        rows(MOODS,"entries_v1").forEach { add("MOOD",it.getLong("id").toString(),it) }
        raw(PLANNER,"event_details")?.let(::JSONObject)?.optJSONArray("events")?.objects()?.forEach {
            add("PLANNER",it.getLong("reminderId").toString(),it)
        }
        raw(WATER,"water_tracker")?.let(::JSONObject)?.let { water ->
            water.getJSONArray("entries").objects().forEach { add("DRINK",it.getString("id"),it) }
            if(water.has("goalMl")) add("PREFERENCE","water/goal",JSONObject().put("value",water.getInt("goalMl")))
        }
        raw(CATEGORIES,"builtin_category_colors")?.let(::JSONObject)?.let { colors ->
            colors.keys().forEach { key -> add("PREFERENCE","colors/$key",JSONObject().put("value",colors.get(key))) }
        }
        (if(file == null || file == DESCRIPTIONS) db.values(scope,DESCRIPTIONS) else emptyMap()).forEach { (key,value) -> add("PREFERENCE","description/$key",JSONObject().put("value",value)) }
        (if(file == null || file == UI) db.values(scope,UI) else emptyMap()).forEach { (key,value) -> add("PREFERENCE","ui/$key",JSONObject().put("value",value)) }
        return result
    }
    /** Only app-owned binary descriptors cross the cloud; never a device path/content URI. */
    private fun transformBinaries(db:SyncDatabase,scope:String,card:JSONObject,upload:Boolean) {
        fun convert(obj:JSONObject,pathKey:String,kind:String,mime:String) {
            if(upload) {
                val path=obj.stringOrNull(pathKey)?.takeIf { it.isNotBlank() }
                    ?: obj.stringOrNull("uri")?.takeIf { it.isNotBlank() }
                if(path!=null) {
                    if(pathKey=="imageUri" && path.startsWith("https://")) return
                    val id=db.blobForPath(scope,card.getLong("id").toString(),path,kind,mime)
                    obj.put("blobId",id)
                }
                obj.remove(pathKey);obj.remove("uri")
            } else {
                obj.stringOrNull("blobId")?.let { id ->
                    val path=checkNotNull(db.blobPath(scope,id)) { "Binary not restored" }
                    obj.put(pathKey,path)
                }
                obj.remove("blobId")
            }
        }
        card.optJSONArray("attachments")?.objects()?.forEach {
            if (it.optString("type") == "TEXT_SNIPPET") { it.remove("localPath"); it.remove("uri") }
            else convert(it, "localPath", if (it.optString("type") == "IMAGE") "IMAGE"
                else if (it.optString("mimeType") == "application/pdf") "PDF" else "FILE",
                it.stringOrNull("mimeType") ?: "application/octet-stream")
        }
        card.optJSONArray("voiceRecordings")?.objects()?.forEach { convert(it, "localPath", "VOICE", "audio/mp4") }
        card.optJSONObject("cardStack")?.optJSONArray("cards")?.objects()?.forEach { convert(it, "imageUri", "IMAGE", "image/jpeg") }

    }
    fun applyRecord(db:SyncDatabase,scope:String,kind:String,key:String,payload:String?) {
        val obj=payload?.let(::JSONObject)
        fun string(file:String,k:String)=db.value(scope,file,k) as String?
        fun set(file:String,k:String,value:Any?)=db.writeValue(scope,file,k,value)
        fun array(file:String,k:String,idKey:String,transform:(JSONObject)->Unit={}) {
            val old=string(file,k)?.let(::JSONArray)?.objects().orEmpty()
            obj?.let(transform)
            val rows=old.filterNot { it.get(idKey).toString()==key }.toMutableList()
            obj?.let(rows::add)
            set(file,k,JSONArray(rows).toString())
        }
        when(kind) {
            // An Ortak kategori card lives in the shared store: a personal record with the same id (its copy from before
            // it was shared) must not overwrite it, or it loses its link and goes up to the category again.
            "CARD"->if(string(CARDS,"reminders")?.let(::JSONArray)?.objects()?.any { row->row.getLong("id").toString()==key && row.has("sharedCardId") && !row.isNull("sharedCardId") } == true) Unit
            else array(CARDS,"reminders","id") {
                transformBinaries(db,scope,it,false)
                // Notification delivery is device-local; restore never claims another device notified it.
                val old=string(CARDS,"reminders")?.let(::JSONArray)?.objects()?.find { row->row.getLong("id").toString()==key }
                it.put("notifiedAlertAt",if(old?.optLong("alertAt")==it.optLong("alertAt")) old?.opt("notifiedAlertAt")?:JSONObject.NULL else JSONObject.NULL)
            }
            "CATEGORY"->array(CATEGORIES,"categories_json","id") {
                // Ortak kategori bookkeeping is device-local: keep it when the cloud copy comes back.
                val old=string(CATEGORIES,"categories_json")?.let(::JSONArray)?.objects()?.find { row->row.get("id").toString()==key }
                for(k in listOf("sharedId","sharedRole","sharedOwnerName")) old?.opt(k)?.takeIf { v->v!=JSONObject.NULL }?.let { v->it.put(k,v) }
            }
            "MOOD"->array(MOODS,"entries_v1","id")
            "PLANNER"->{
                val root=string(PLANNER,"event_details")?.let(::JSONObject)?:JSONObject().put("version",1).put("events",JSONArray())
                val events=root.getJSONArray("events").objects().filterNot { it.getLong("reminderId").toString()==key }.toMutableList()
                obj?.let(events::add);root.put("events",JSONArray(events));set(PLANNER,"event_details",root.toString())
            }
            "DRINK"->{
                val root=string(WATER,"water_tracker")?.let(::JSONObject)?:JSONObject().put("schemaVersion",2).put("entries",JSONArray())
                val entries=root.getJSONArray("entries").objects().filterNot { it.getString("id")==key }.toMutableList()
                obj?.let(entries::add);root.put("entries",JSONArray(entries));set(WATER,"water_tracker",root.toString())
            }
            "PREFERENCE"->when {
                key=="water/goal"->{
                    val root=string(WATER,"water_tracker")?.let(::JSONObject)?:JSONObject().put("schemaVersion",2).put("entries",JSONArray())
                    root.put("goalMl",obj?.optInt("value",2000)?:2000);set(WATER,"water_tracker",root.toString())
                }
                key.startsWith("colors/")->{
                    val root=string(CATEGORIES,"builtin_category_colors")?.let(::JSONObject)?:JSONObject()
                    if(obj==null)root.remove(key.removePrefix("colors/")) else root.put(key.removePrefix("colors/"),obj.get("value"))
                    set(CATEGORIES,"builtin_category_colors",root.toString())
                }
                key.startsWith("description/")->set(DESCRIPTIONS,key.removePrefix("description/"),obj?.opt("value"))
                key.startsWith("ui/")-> { val k=key.removePrefix("ui/");check(k in uiKeys);set(UI,k,obj?.opt("value")) }
                else->error("Unsupported preference")
            }
            else->error("Unsupported sync kind")
        }
    }
}
