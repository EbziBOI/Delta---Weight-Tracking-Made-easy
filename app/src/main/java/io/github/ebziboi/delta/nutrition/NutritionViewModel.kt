package io.github.ebziboi.delta.nutrition

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.ebziboi.delta.DeltaApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NutritionViewModel(app: Application): AndroidViewModel(app) {
    val repo=(app as DeltaApp).nutrition
    val state=repo.state
    val vault=PhotoKeyVault(app)
    private val off=OpenFoodFacts()
    var busy by mutableStateOf(false);private set
    var message by mutableStateOf<String?>(null)
    var results by mutableStateOf<List<Food>>(emptyList());private set
    var selectedStore by mutableStateOf<String?>(null);private set
    private val stores=StorePreferences(app)
    internal var storeOrder by mutableStateOf(stores.ranked());private set
    /** Choosing a store mid-search replaces that search rather than being ignored. */
    fun chooseStore(id:String?) {
        if(busy && !searching) return
        if(searching) {cancel();searching=false}
        selectedStore=id;results=emptyList();searched=false;storeOrder=stores.ranked()
    }
    var searched by mutableStateOf(false);private set
    /** True only while a product search runs, unlike [busy], which any action sets. */
    var searching by mutableStateOf(false);private set
    var selectedFood by mutableStateOf<Food?>(null)
    var selectedEntry by mutableStateOf<DiaryEntry?>(null)
    /** A portion typed with the search ("200g", "2 eggs"), applied to the next portion page. */
    internal var pendingPortion by mutableStateOf<QueryPortion?>(null)
    var recipeIngredients by mutableStateOf<List<Ingredient>>(emptyList())
    var foodDraftId by mutableStateOf(newId())
    var recipeDraftId by mutableStateOf(newId())
    var editingRecipe by mutableStateOf<Food?>(null)
    var photoUri by mutableStateOf<Uri?>(null)
    var photoReview by mutableStateOf<List<Ingredient>>(emptyList())
    var savedProviders by mutableStateOf(PhotoProvider.entries.filter { vault.has(it) }.toSet());private set
    private var job: Job?=null
    private var generation=0
    fun run(action: suspend () -> Unit) {
        if(busy) return
        val token=++generation
        job=viewModelScope.launch {
            busy=true;message=null
            try { action() }
            catch(e: CancellationException) { throw e }
            catch(e: FoodNetworkException) { message=e.message }
            catch(e: IllegalArgumentException) { message=e.message?.take(200) ?: "Check the entered values" }
            catch(e: IllegalStateException) { message=e.message?.take(200) ?: "This action could not be completed" }
            catch(_: Exception) { message="Could not complete this action. Check the values and connection, then try again." }
            finally { if(token==generation) busy=false }
        }
    }
    fun cancel() { generation++;job?.cancel();busy=false }
    /** A [quiet] search (started by a pause in typing) never shows an error; pressing search does. */
    fun search(query: String,quiet:Boolean=false) = run {
        searching=true
        try { results=off.search(query,selectedStore);if(results.isNotEmpty()) selectedStore?.let {stores.recordChoice(it)};searched=true;storeOrder=stores.ranked() }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { searched=false;results=emptyList();if(!quiet) throw e }
        finally { searching=false }
    }
    /** Fills in a search result's serving size, pack and stores from its full product record. */
    internal suspend fun details(food:Food)=if(food.source==FoodSource.OPEN_FOOD_FACTS) off.details(food) else food
    /** Opens a search result once its full record (with serving size) has loaded. */
    fun openProduct(food:Food,onFound:(Food)->Unit)=run {onFound(details(food))}
    /** A short-lived "Undo" offer for the last diary change. */
    class Undo(val label:String,val action:suspend ()->Unit)
    var undo by mutableStateOf<Undo?>(null)
    private var undoJob:Job?=null
    fun offerUndo(label:String,action:suspend ()->Unit) {
        undo=Undo(label,action);undoJob?.cancel()
        undoJob=viewModelScope.launch {kotlinx.coroutines.delay(6000);undo=null}
    }
    fun performUndo() {val u=undo ?: return;undo=null;undoJob?.cancel();run {u.action()}}
    fun barcode(code:String,onFound:(Food)->Unit,onMissing:((String)->Unit)?=null,onIncomplete:((Food)->Unit)?=null)=run {resolveBarcode(code,onFound,onMissing,onIncomplete)}
    private suspend fun resolveBarcode(code:String,onFound:(Food)->Unit,onMissing:((String)->Unit)?,onIncomplete:((Food)->Unit)?=null) {
        val wanted=ProductPages.canonicalBarcode(code) ?: throw IllegalArgumentException("Enter a valid barcode")
        val saved=repo.state.value.foods.firstOrNull {ProductPages.canonicalBarcode(it.barcode)==wanted && !it.missingNutrition}
        if(saved!=null) {onFound(saved);return}
        val food=try {off.barcode(code)} catch(e:CancellationException) {throw e} catch(e:Exception) {
            if(onMissing==null) throw e
            message="Database lookup unavailable. Try the product page or its label.";null
        }
        // A product the database knows but without label values goes straight to manual entry.
        if(food!=null && food.missingNutrition && onIncomplete!=null) onIncomplete(food)
        else if(food!=null) onFound(food)
        else if(onMissing!=null) onMissing(code)
        else throw IllegalArgumentException("Product not found. Import its product page or read its label.")
    }
    internal fun barcodePhoto(uri:Uri,crop:LabelCrop,onFound:(Food)->Unit,onMissing:((String)->Unit)?,onIncomplete:((Food)->Unit)?=null)=run {
        val code=BarcodePhotos.decode(getApplication(),uri,crop) ?: throw IllegalArgumentException("No barcode recognised. Crop closer, rotate the photo, or enter the number.")
        resolveBarcode(code,onFound,onMissing,onIncomplete)
    }
    fun analyse(provider: PhotoProvider) = run {
        val uri=photoUri ?: error("Choose a photo first")
        photoReview=PhotoNutrition(getApplication(),vault).analyse(uri,provider)
    }
    fun saveKey(provider: PhotoProvider,key: String,model: String,onSaved: ()->Unit) = run {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { vault.save(provider,key,model) }
        savedProviders=savedProviders+provider;onSaved()
    }
    fun removeKey(provider: PhotoProvider) = run {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { vault.remove(provider) }
        savedProviders=savedProviders-provider
    }
    fun clearPhoto() {
        photoUri?.takeIf { it.authority=="${getApplication<Application>().packageName}.files" }?.let { uri ->
            runCatching { getApplication<Application>().contentResolver.delete(uri,null,null) }
        }
        photoUri=null;photoReview=emptyList()
    }
    override fun onCleared() { clearPhoto();super.onCleared() }
}
