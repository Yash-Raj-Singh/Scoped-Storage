package com.example.scopedstorage

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.scopedstorage.databinding.ItemPictureBinding

class SharedPictureAdapter(
    private val onPictureClick: (SharedStoragePicture) -> Unit
) : ListAdapter<SharedStoragePicture, SharedPictureAdapter.PictureViewHolder>(Companion) {

    inner class PictureViewHolder(val binding: ItemPictureBinding): RecyclerView.ViewHolder(binding.root)

    companion object : DiffUtil.ItemCallback<SharedStoragePicture>() {
        override fun areItemsTheSame(oldItem: SharedStoragePicture, newItem: SharedStoragePicture): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SharedStoragePicture, newItem: SharedStoragePicture): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PictureViewHolder {
        return PictureViewHolder(
            ItemPictureBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: PictureViewHolder, position: Int) {
        val Picture = currentList[position]
        holder.binding.apply {
            ivPicture.setImageURI(Picture.contentUri)

            val aspectRatio = Picture.width.toFloat() / Picture.height.toFloat()
            ConstraintSet().apply {
                clone(root)
                setDimensionRatio(ivPicture.id, aspectRatio.toString())
                applyTo(root)
            }

            ivPicture.setOnLongClickListener {
                onPictureClick(Picture)
                true
            }
        }
    }
}