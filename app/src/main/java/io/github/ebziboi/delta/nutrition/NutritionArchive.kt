package io.github.ebziboi.delta.nutrition

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal object NutritionArchive {
    fun encode(state:NutritionState):JSONObject {
        check(state.loaded && state.loadError==null)
        return JSONObject().put("foods",JSONArray().also {a->state.foods.forEach {a.put(it.json())}})
            .put("entries",JSONArray().also {a->state.entries.forEach {e->a.put(JSONObject().put("id",e.id).put("day",e.day.toEpochDay()).put("meal",e.meal.name).put("food",e.food.json()).put("amount",e.amount).put("created",e.created))}})
            .put("targets",JSONObject().put("amounts",state.targets.amounts.toJson()).put("mode",state.targets.mode.name).put("percentages",state.targets.percentages.toJson()).put("limits",JSONArray(state.targets.limits.map {it.name})))
            .put("fasts",JSONArray().also {a->state.fasts.forEach {f->a.put(JSONObject().put("id",f.id).put("start",f.start).put("hours",f.targetHours).put("end",f.end))}})
    }
    private fun validateFood(j:JSONObject,depth:Int=0) {
        require(depth<10 && j.getString("id").length in 1..250)
        validateNutrients(j.getJSONObject("nutrients"))
        listOf("serving","pack","servings","yield").forEach {key->if(j.has(key) && !j.isNull(key)) require(j.getDouble(key).let {it.isFinite() && it>0 && it<=100000})}
        if(j.has("sourceUrl") && !j.isNull("sourceUrl")) require(ProductPages.publicUrl(j.getString("sourceUrl")))
        val ingredients=j.getJSONArray("ingredients");require(ingredients.length()<=200)
        (0 until ingredients.length()).forEach {validateFood(ingredients.getJSONObject(it).getJSONObject("food"),depth+1)}
    }
    private fun validateNutrients(j:JSONObject) {j.keys().forEach {key->require(Nutrient.entries.any {it.name==key});require(j.getDouble(key).validAmount())}}
    fun parse(j:JSONObject):NutritionState {
        val foods=j.getJSONArray("foods");val entries=j.getJSONArray("entries");val fasts=j.getJSONArray("fasts")
        require(foods.length()<=20000 && entries.length()<=100000 && fasts.length()<=20000)
        (0 until foods.length()).forEach {validateFood(foods.getJSONObject(it))}
        (0 until entries.length()).forEach {validateFood(entries.getJSONObject(it).getJSONObject("food"))}
        val t=j.getJSONObject("targets");val limits=t.getJSONArray("limits")
        validateNutrients(t.getJSONObject("amounts"));validateNutrients(t.getJSONObject("percentages"))
        val parsed=NutritionState((0 until foods.length()).map {foodFromJson(foods.getJSONObject(it))},
            (0 until entries.length()).map {i->val e=entries.getJSONObject(i);DiaryEntry(e.getString("id"),LocalDate.ofEpochDay(e.getLong("day")),Meal.valueOf(e.getString("meal")),foodFromJson(e.getJSONObject("food")),e.getDouble("amount"),e.getLong("created"))},
            NutritionTargets(t.getJSONObject("amounts").nutrients(),MacroMode.valueOf(t.getString("mode")),t.getJSONObject("percentages").nutrients(),(0 until limits.length()).map {Nutrient.valueOf(limits.getString(it))}.toSet()).validated(),
            (0 until fasts.length()).map {i->val f=fasts.getJSONObject(i);Fast(f.getString("id"),f.getLong("start"),f.getDouble("hours"),if(f.isNull("end")) null else f.getLong("end"))},loaded=true)
        require(parsed.foods.map {it.id}.distinct().size==parsed.foods.size && parsed.entries.map {it.id}.distinct().size==parsed.entries.size)
        require(parsed.fasts.count {it.end==null}<=1 && parsed.fasts.map {it.id}.distinct().size==parsed.fasts.size)
        require(parsed.entries.all {it.id.length<=250 && it.created>=0 && (!it.fromHealthConnect || it.food.source==FoodSource.HEALTH_CONNECT)})
        return parsed
    }
}
