package com.ducktapedapps.updoot.ui.subreddit

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.ducktapedapps.updoot.R
import com.ducktapedapps.updoot.UpdootApplication
import com.ducktapedapps.updoot.databinding.FragmentSubredditBinding
import com.ducktapedapps.updoot.model.LinkData
import com.ducktapedapps.updoot.ui.ActivityVM
import com.ducktapedapps.updoot.ui.MediaPreviewFragmentDirections
import com.ducktapedapps.updoot.utils.*
import javax.inject.Inject

class SubredditFragment : Fragment() {
    @Inject
    lateinit var appContext: Application

    private lateinit var submissionsVM: SubmissionsVM
    private val args: SubredditFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity?.application as UpdootApplication).updootComponent.inject(this@SubredditFragment)
        submissionsVM = ViewModelProvider(this@SubredditFragment,
                SubmissionsVMFactory(appContext, args.rSubreddit ?: "")
        ).get(SubmissionsVM::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentSubredditBinding.inflate(inflater, container, false)
                .apply { lifecycleOwner = viewLifecycleOwner }

        val adapter = SubmissionsAdapter(ClickHandler())

        setUpVMWithViews(binding, adapter)
        setUpRecyclerView(binding, adapter)
        return binding.root
    }

    private fun setUpRecyclerView(binding: FragmentSubredditBinding, adapter: SubmissionsAdapter) {
        val recyclerView = binding.recyclerView
        val linearLayoutManager = LinearLayoutManager(this@SubredditFragment.context)

        recyclerView.apply {
            this.adapter = adapter
            layoutManager = linearLayoutManager
            itemAnimator = CustomItemAnimator()
        }

        ItemTouchHelper(SwipeUtils(activity, object : SwipeUtils.SwipeActionCallback {
            override fun performSlightLeftSwipeAction(adapterPosition: Int) = submissionsVM.castVote(adapterPosition, -1)


            override fun performSlightRightSwipeAction(adapterPosition: Int) = submissionsVM.castVote(adapterPosition, 1)


            override fun performLeftSwipeAction(adapterPosition: Int) =
                    showMenuFor(args.rSubreddit,
                            adapter.currentList[adapterPosition],
                            this@SubredditFragment.requireContext(),
                            recyclerView.findViewHolderForAdapterPosition(adapterPosition)?.itemView,
                            findNavController())

            override fun performRightSwipeAction(adapterPosition: Int) {
                submissionsVM.toggleSave(adapterPosition)
            }
        })).attachToRecyclerView(recyclerView)
        recyclerView.addOnScrollListener(InfiniteScrollListener(linearLayoutManager, submissionsVM))
        val swipeRefreshLayout = binding.swipeToRefreshLayout
        swipeRefreshLayout.setColorSchemeResources(
                R.color.DT_primaryColor,
                R.color.secondaryColor,
                R.color.secondaryDarkColor)
        swipeRefreshLayout.setOnRefreshListener { reloadFragmentContent() }
    }

    private fun setUpVMWithViews(binding: FragmentSubredditBinding, adapter: SubmissionsAdapter) {
        binding.submissionViewModel = submissionsVM
        activity?.let {
            val activityVM = ViewModelProvider(it).get(ActivityVM::class.java)
            activityVM.currentAccount.observe(viewLifecycleOwner, Observer { account: SingleLiveEvent<String?>? ->
                if (account?.contentIfNotHandled != null) {
                    reloadFragmentContent()
                    Toast.makeText(this.context, account.peekContent().toString() + " is logged in!", Toast.LENGTH_SHORT).show()
                }
            })
        }

        submissionsVM.allSubmissions.observe(viewLifecycleOwner, Observer { things: List<LinkData>? -> adapter.submitList(things) })
        submissionsVM.toastMessage.observe(viewLifecycleOwner, Observer { toastMessage: SingleLiveEvent<String?> ->
            val toast = toastMessage.contentIfNotHandled
            if (toast != null) Toast.makeText(this.context, toast, Toast.LENGTH_SHORT).show()
        })

    }

    private fun reloadFragmentContent() {
        submissionsVM.reload(null, null)
    }

    inner class ClickHandler {
        fun onClick(linkData: LinkData) {
            val action = SubredditFragmentDirections.actionGoToComments(linkData)
            findNavController().navigate(action)
        }

        fun handleImagePreview(data: LinkData) {
            findNavController().navigate(
                    MediaPreviewFragmentDirections.actionGlobalMediaPreviewFragment(
                            data.preview!!.images[0].source.url
                    )
            )
        }

        fun handleExpansion(index: Int) {
            submissionsVM.expandSelfText(index)
        }
    }


}