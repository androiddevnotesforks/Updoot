package com.ducktapedapps.updoot.ui.comments

import android.text.Spanned
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ducktapedapps.updoot.api.local.SubmissionsCacheDAO
import com.ducktapedapps.updoot.model.LinkData
import com.ducktapedapps.updoot.ui.comments.SubmissionContent.*
import com.ducktapedapps.updoot.utils.Media
import com.ducktapedapps.updoot.utils.linkMetaData.LinkModel
import com.ducktapedapps.updoot.utils.linkMetaData.extractMetaData
import com.ducktapedapps.updoot.utils.linkMetaData.fetchMetaDataFrom
import com.ducktapedapps.updoot.utils.linkMetaData.toLinkModel
import com.ducktapedapps.updoot.utils.toMedia
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.URI
import javax.inject.Inject

@ExperimentalCoroutinesApi
class CommentsVM(
        private val repo: CommentsRepo,
        submissionsCacheDAO: SubmissionsCacheDAO,
        private val markwon: Markwon,
        private val id: String,
        private val subreddit_name: String
) : ViewModel() {
    val allComments = repo.allComments

    private val _contentLoading = MutableStateFlow(true)

    val submissionData: Flow<LinkData> = submissionsCacheDAO
            .observeLinkData(id)
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    val content: Flow<SubmissionContent> = submissionData.transform {
        emit(
                when (val data = it.toMedia()) {
                    is Media.SelfText -> SelfText(markwon.toMarkdown(data.text))
                    is Media.Image -> Image(data)
                    is Media.Video -> Video(data)
                    is Media.Link ->
                        try {
                            URI.create(data.url).getMetaData()
                        } catch (e: Exception) {
                            JustTitle
                        } finally {
                            _contentLoading.value = false
                        }
                    Media.JustTitle -> JustTitle
                }

        )
    }


    val isLoading = repo.commentsAreLoading

    init {
        loadComments()
    }

    private fun loadComments() {
        viewModelScope.launch {
            repo.loadComments(subreddit_name, id)
        }
    }

    fun toggleChildrenVisibility(index: Int) {
        viewModelScope.launch {
            repo.toggleChildrenCommentVisibility(index, id)
        }
    }

    fun castVote(direction: Int, index: Int) = Unit


    @Throws(Exception::class)
    private suspend fun URI.getMetaData(): Link {
        _contentLoading.value = true
        val htmlResponse = fetchMetaDataFrom(this.toString())
        return Link(htmlResponse.extractMetaData().toLinkModel(this.toString()))
    }
}

sealed class SubmissionContent {
    data class Image(val data: Media.Image) : SubmissionContent()
    data class Video(val data: Media.Video) : SubmissionContent()
    data class SelfText(val parsedMarkdown: Spanned) : SubmissionContent()
    data class Link(val linkModel: LinkModel) : SubmissionContent()
    object JustTitle : SubmissionContent()
}

@ExperimentalCoroutinesApi
class CommentsVMFactory @Inject constructor(
        private val commentsRepo: CommentsRepo,
        private val submissionsCacheDAO: SubmissionsCacheDAO,
        private val markwon: Markwon
) : ViewModelProvider.Factory {
    private lateinit var subredditName: String
    private lateinit var id: String

    fun setSubredditAndId(name: String, id: String) {
        subredditName = name
        this.id = id
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T =
            CommentsVM(commentsRepo, submissionsCacheDAO, markwon, id, subredditName) as T
}