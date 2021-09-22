package com.example.scopedstorage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.scopedstorage.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var internalStoragePictureAdapter: InternalStoragePictureAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        internalStoragePictureAdapter = InternalStoragePictureAdapter {

        }
        setupInternalStorageRecyclerView()
        loadPicturesFromInternalStorageIntoRecyclerview()
    }

    private fun setupInternalStorageRecyclerView() = binding.rvPrivatePhotos.apply {
        adapter = internalStoragePictureAdapter
        layoutManager = LinearLayoutManager(this@MainActivity)
    }

    private fun loadPicturesFromInternalStorageIntoRecyclerview(){
        lifecycleScope.launch {
            val pictures = loadPicturesFromInternalStorage()

            internalStoragePictureAdapter.submitList(pictures)
        }
    }

    private fun deletePictureFromInternalStorage(filename: String) : Boolean{
        return try {
                deleteFile(filename)
        } catch (e : Exception)
        {
            Toast.makeText(parent, e.printStackTrace().toString(), Toast.LENGTH_SHORT).show()
            false
        }
    }

    private suspend fun loadPicturesFromInternalStorage() : List<InternalStoragePicture> {
        return withContext(Dispatchers.IO)
        {
            val files = filesDir.listFiles()
            files?.filter { it.canRead() && it.isFile && (it.name.endsWith(".jpg") || it.name.endsWith(".jpeg")) }?.map {
                val bytes = it.readBytes()
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                InternalStoragePicture(it.name, bmp)
            }?: listOf()
        }
    }

    private fun savePictureToInternalStorage(filename : String, bmp : Bitmap): Boolean
    {
        return  try {
            openFileOutput("$filename.jpg", MODE_PRIVATE).use { stream ->
                if(!bmp.compress(Bitmap.CompressFormat.JPEG, 95, stream))
                {
                    throw IOException("Couldn't save this!")
                }
            }
            return true
        }catch (e : IOException)
        {
            Toast.makeText(parent, e.printStackTrace().toString(), Toast.LENGTH_SHORT).show()
            false
        }
    }
}