package com.example.scopedstorage

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.scopedstorage.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.lang.Exception
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var internalStoragePictureAdapter: InternalStoragePictureAdapter

    private var readPermissionGranted = false
    private var writePermissionGranted = false

    private lateinit var permissionsLauncher : ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        internalStoragePictureAdapter = InternalStoragePictureAdapter {
            val isDeleted = deletePictureFromInternalStorage(it.name)

            if(isDeleted)
            {
                loadPicturesFromInternalStorageIntoRecyclerview()
                Toast.makeText(this, "Picture Deleted!", Toast.LENGTH_SHORT).show()
            }
            else
            {
                Toast.makeText(this, "Error Occurred!", Toast.LENGTH_SHORT).show()
            }
        }

        val takePicture = registerForActivityResult(ActivityResultContracts.TakePicturePreview()){
            val isPrivate = binding.switchPrivate.isChecked

            if(isPrivate)
            {
                val isSavedSuccessfully =  savePictureToInternalStorage(UUID.randomUUID().toString(), it)

                if(isSavedSuccessfully)
                {
                    loadPicturesFromInternalStorageIntoRecyclerview()
                    Toast.makeText(this, "Picture Saved!", Toast.LENGTH_SHORT).show()
                }
                else
                {
                    Toast.makeText(this, "Error Occurred!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnTakePhoto.setOnClickListener {
            takePicture.launch()
        }

        setupInternalStorageRecyclerView()
        loadPicturesFromInternalStorageIntoRecyclerview()
    }

    private fun updateOrRequestPermissions(){
        val hasReadPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val hasWritePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val minSDK29 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        readPermissionGranted = hasReadPermission
        writePermissionGranted = hasWritePermission || minSDK29

        val permissionsToRequest = mutableListOf<String>()

        if(!writePermissionGranted)
        {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if(!readPermissionGranted)
        {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if(permissionsToRequest.isNotEmpty())
        {
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun setupInternalStorageRecyclerView() = binding.rvPrivatePhotos.apply {
        adapter = internalStoragePictureAdapter
        layoutManager = StaggeredGridLayoutManager(2, RecyclerView.VERTICAL)
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
                if(!bmp.compress(Bitmap.CompressFormat.JPEG, 100, stream))
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