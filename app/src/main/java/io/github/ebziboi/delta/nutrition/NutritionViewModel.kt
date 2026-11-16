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
    var searched by mutableStateOf(false);private set
    var selectedFood by mutableStateOf<Food?>(null)
    var selectedEntry by mutableStateOf<DiaryEntry?>(null)
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
    fun search(query: String) = run { results=off.search(query);searched=true }
    fun barcode(code: String,onFound: (Food)->Unit) = run {
        val food=off.barcode(code) ?: throw IllegalArgumentException("Product not found. You can create a custom food from its label.")
        onFound(food)
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
