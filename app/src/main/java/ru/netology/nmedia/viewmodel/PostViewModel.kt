package ru.netology.nmedia.viewmodel

import android.net.Uri
import androidx.core.net.toFile
import androidx.lifecycle.*
import androidx.paging.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.netology.nmedia.auth.AppAuth
import ru.netology.nmedia.dto.FeedItem
import ru.netology.nmedia.dto.MediaUpload
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.dto.Separator
import ru.netology.nmedia.model.FeedModelState
import ru.netology.nmedia.model.PhotoModel
import ru.netology.nmedia.repository.PostRepository
import ru.netology.nmedia.util.SingleLiveEvent
import javax.inject.Inject

private val empty = Post(
    id = 0,
    content = "",
    authorId = 0,
    author = "",
    authorAvatar = "",
    likedByMe = false,
    likes = 0,
    published = 0,
)

private val noPhoto = PhotoModel()

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PostViewModel @Inject constructor(
    private val repository: PostRepository,
    auth: AppAuth,
) : ViewModel() {
    val data: Flow<PagingData<FeedItem>> = auth.authStateFlow
        .flatMapLatest { (myId, _) ->
            repository
                .data
                .map { pagingData ->
                    pagingData.map { item ->
                        item.copy(ownedByMe = item.authorId == myId)
                    }
                }
                .map { pagingData ->
                    pagingData.insertSeparators(
                        terminalSeparatorType = TerminalSeparatorType.SOURCE_COMPLETE,
                        generator = { before, after ->
                            when (getSeparatorDay(before, after)) {
                                PostPublishedTime.NOTHING -> null
                                PostPublishedTime.TODAY -> Separator("Сегодня")
                                PostPublishedTime.YESTERDAY -> Separator("Вчера")
                                PostPublishedTime.LAST_WEEK -> Separator("На прошлой неделе")
                            }
                        }
                    )
                }

        }
        .cachedIn(viewModelScope)

    private fun getSeparatorDay(before: Post?, after: Post?): PostPublishedTime {
        val currentTimeInSeconds = System.currentTimeMillis() / 1_000
        val afterDate = getPostPublishedTime(after?.published, currentTimeInSeconds)
        val beforeDate = getPostPublishedTime(before?.published, currentTimeInSeconds)
        return when {
            beforeDate != PostPublishedTime.TODAY && afterDate == PostPublishedTime.TODAY -> PostPublishedTime.TODAY
            beforeDate != PostPublishedTime.YESTERDAY && afterDate == PostPublishedTime.YESTERDAY -> PostPublishedTime.YESTERDAY
            beforeDate != PostPublishedTime.LAST_WEEK && afterDate == PostPublishedTime.LAST_WEEK -> PostPublishedTime.LAST_WEEK
            else -> PostPublishedTime.NOTHING
        }
    }

    private fun getPostPublishedTime(
        published: Long?,
        currentTimeSeconds: Long
    ): PostPublishedTime {
        val secondInDay = 60 * 60 * 24

        return when {
            published == null -> PostPublishedTime.NOTHING
            currentTimeSeconds - published <= secondInDay -> PostPublishedTime.TODAY
            currentTimeSeconds - published <= 2 * secondInDay -> PostPublishedTime.YESTERDAY
            else -> PostPublishedTime.LAST_WEEK
        }
    }

    private enum class PostPublishedTime { NOTHING, TODAY, YESTERDAY, LAST_WEEK }

    private val _dataState = MutableLiveData<FeedModelState>()
    val dataState: LiveData<FeedModelState>
        get() = _dataState

    private val edited = MutableLiveData(empty)
    private val _postCreated = SingleLiveEvent<Unit>()
    val postCreated: LiveData<Unit>
        get() = _postCreated

    private val _photo = MutableLiveData(noPhoto)
    val photo: LiveData<PhotoModel>
        get() = _photo

    init {
        loadPosts()
    }

    fun loadPosts() = viewModelScope.launch {
        try {
            _dataState.value = FeedModelState(loading = true)
            // repository.stream.cachedIn(viewModelScope).
            _dataState.value = FeedModelState()
        } catch (e: Exception) {
            _dataState.value = FeedModelState(error = true)
        }
    }

    fun refreshPosts() = viewModelScope.launch {
        try {
            _dataState.value = FeedModelState(refreshing = true)
//            repository.getAll()
            _dataState.value = FeedModelState()
        } catch (e: Exception) {
            _dataState.value = FeedModelState(error = true)
        }
    }

    fun save() {
        edited.value?.let {
            viewModelScope.launch {
                try {
                    repository.save(
                        it, _photo.value?.uri?.let { MediaUpload(it.toFile()) }
                    )

                    _postCreated.value = Unit
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        edited.value = empty
        _photo.value = noPhoto
    }

    fun edit(post: Post) {
        edited.value = post
    }

    fun changeContent(content: String) {
        val text = content.trim()
        if (edited.value?.content == text) {
            return
        }
        edited.value = edited.value?.copy(content = text)
    }

    fun changePhoto(uri: Uri?) {
        _photo.value = PhotoModel(uri)
    }

    fun likeById(id: Long) {
        TODO()
    }

    fun removeById(id: Long) {
        TODO()
    }
}
