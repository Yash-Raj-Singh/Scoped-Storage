package com.example.scopedstorage

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.scopedstorage.databinding.ItemPictureBinding

class InternalStoragePictureAdapter(
    private val onPhotoClick : (InternalStoragePicture) -> Unit
) : ListAdapter<InternalStoragePicture, InternalStoragePictureAdapter.PictureViewHolder>(Companion)
{
    inner class PictureViewHolder(val binding : ItemPictureBinding) : RecyclerView.ViewHolder(binding.root)

    companion object :  DiffUtil.ItemCallback<InternalStoragePicture>(){
        override fun areItemsTheSame(
            oldItem: InternalStoragePicture,
            newItem: InternalStoragePicture
        ): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(
            oldItem: InternalStoragePicture,
            newItem: InternalStoragePicture
        ): Boolean {
            return oldItem.name == newItem.name && oldItem.bmp.sameAs(newItem.bmp)
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
        val picture = currentList[position]

        holder.binding.apply{
            ivPicture.setImageBitmap(picture.bmp)

            val aspectRatio = picture.bmp.width.toFloat() / picture.bmp.height.toFloat()
            ConstraintSet().apply {
                clone(root)
                setDimensionRatio(ivPicture.id, aspectRatio.toString())
                applyTo(root)
            }

            ivPicture.setOnLongClickListener {
                onPhotoClick(picture)
                true
            }
        }
    }
}