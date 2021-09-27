package com.example.scopedstorage

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
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
    private lateinit var externalStoragePictureAdapter: SharedPictureAdapter

    private var readPermissionGranted = false
    private var writePermissionGranted = false

    private lateinit var permissionsLauncher : ActivityResultLauncher<Array<String>>

    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>

    private lateinit var contentObserver : ContentObserver

    private var deletedImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        internalStoragePictureAdapter = InternalStoragePictureAdapter {
            lifecycleScope.launch {
                val isDeleted = deletePictureFromInternalStorage(it.name)

                if(isDeleted)
                {
                    loadPicturesFromInternalStorageIntoRecyclerview()
                    Toast.makeText(this@MainActivity, "Picture Deleted!", Toast.LENGTH_SHORT).show()
                }
                else
                {
                    Toast.makeText(this@MainActivity, "Error Occurred!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        externalStoragePictureAdapter = SharedPictureAdapter {
            lifecycleScope.launch {
                deletePictureFromExternalStorage(it.contentUri)
                deletedImageUri = it.contentUri
            }
        }

        setupExternalStorageRecyclerView()
        initContentObserver()

        if(readPermissionGranted)
        {
            loadPicturesFromExternalStorageIntoRecyclerview()
        }
        else
        {
            Toast.makeText(this, "Permissions Denied!", Toast.LENGTH_SHORT).show()
        }

        permissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            readPermissionGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE]?: readPermissionGranted
            writePermissionGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE]?: writePermissionGranted

        }

        updateOrRequestPermissions()

        intentSenderLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()){
            if(it.resultCode == RESULT_OK)
            {
                if(Build.VERSION.SDK_INT == Build.VERSION_CODES.Q)
                {
                    lifecycleScope.launch {
                        deletePictureFromExternalStorage(deletedImageUri?:return@launch)
                    }
                }
                Toast.makeText(this, "Picture is in Trash!", Toast.LENGTH_SHORT).show()
            }
            else
            {
                Toast.makeText(this, "Not able to delete!", Toast.LENGTH_SHORT).show()
            }
        }

        val takePicture = registerForActivityResult(ActivityResultContracts.TakePicturePreview()){
            lifecycleScope.launch {
                val isPrivate = binding.switchPrivate.isChecked

                val isSavedSuccessfully =  when {
                    isPrivate-> savePictureToInternalStorage(UUID.randomUUID().toString(), it)
                    writePermissionGranted -> savePictureToExternalStorage(UUID.randomUUID().toString() ,it)
                    else -> false
                }

                if(isPrivate)
                {
                    loadPicturesFromInternalStorageIntoRecyclerview()
                }

                if(isSavedSuccessfully)
                {
                    Toast.makeText(this@MainActivity, "Picture Saved!", Toast.LENGTH_SHORT).show()
                }
                else
                {
                    Toast.makeText(this@MainActivity, "Error Occurred!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnTakePhoto.setOnClickListener {
            takePicture.launch()
        }

        setupInternalStorageRecyclerView()
        loadPicturesFromInternalStorageIntoRecyclerview()
        loadPicturesFromExternalStorageIntoRecyclerview()
    }

    private fun initContentObserver()
    {
        contentObserver = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                if (readPermissionGranted){
                    loadPicturesFromExternalStorageIntoRecyclerview()
                }
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    private suspend fun deletePictureFromExternalStorage(pictureUri : Uri){
        withContext(Dispatchers.IO)
        {
            try {
                contentResolver.delete(pictureUri, null, null)
            }catch (e: SecurityException)
            {
                val intentSender = when{
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    {
                        MediaStore.createTrashRequest(contentResolver, listOf(pictureUri), true).intentSender
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->{
                        val recoverableSecurityException = e as? RecoverableSecurityException

                        recoverableSecurityException?.userAction?.actionIntent?.intentSender
                    }
                    else->null
                }
                intentSender?.let {
                    sender->
                    intentSenderLauncher.launch(
                        IntentSenderRequest.Builder(sender).build()
                    )
                }
            }
        }
    }

    private suspend fun loadPicturesFromExternalStorage(): List<SharedStoragePicture>{
        return withContext(Dispatchers.IO)
        {
            val collection = sdk29AndUp {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            }?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.WIDTH
            )

            val pictures = mutableListOf<SharedStoragePicture>()

            contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)

                while (cursor.moveToNext())
                {
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(displayNameColumn)
                    val height = cursor.getInt(heightColumn)
                    val width = cursor.getInt(widthColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    pictures.add(SharedStoragePicture(id, displayName, width, height, contentUri))
                }
                pictures.toList()
            } ?: listOf()
        }
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

    private suspend fun savePictureToExternalStorage(displayName: String, bmp: Bitmap) : Boolean
    {
        return withContext(Dispatchers.IO)
        {
            val imageCollection = sdk29AndUp {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.WIDTH, bmp.width)
                put(MediaStore.Images.Media.HEIGHT, bmp.height)

            }

            try {
                contentResolver.insert(imageCollection, contentValues)?.also { uri ->
                    contentResolver.openOutputStream(uri).use { outputStream ->
                        if(!bmp.compress(Bitmap.CompressFormat.JPEG, 100 , outputStream)){
                            throw IOException("Error Occurred in Saving Picture!")
                        }
                    }
                } ?: throw IOException("Error Occurred in Creation of MediaStore!")
                true
            }catch (e : IOException)
            {
                Toast.makeText(parent, e.printStackTrace().toString(), Toast.LENGTH_SHORT).show()
                false
            }
        }

    }

    private fun setupInternalStorageRecyclerView() = binding.rvPrivatePhotos.apply {
        adapter = internalStoragePictureAdapter
        layoutManager = StaggeredGridLayoutManager(2, RecyclerView.VERTICAL)
    }

    private fun setupExternalStorageRecyclerView() = binding.rvPublicPhotos.apply {
        adapter = externalStoragePictureAdapter
        layoutManager = StaggeredGridLayoutManager(2, RecyclerView.VERTICAL)
    }

    private fun loadPicturesFromInternalStorageIntoRecyclerview(){
        lifecycleScope.launch {
            val pictures = loadPicturesFromInternalStorage()

            internalStoragePictureAdapter.submitList(pictures)
        }
    }

    private fun loadPicturesFromExternalStorageIntoRecyclerview(){
        lifecycleScope.launch {
            val pictures = loadPicturesFromExternalStorage()

            externalStoragePictureAdapter.submitList(pictures)
        }
    }

    private suspend fun deletePictureFromInternalStorage(filename: String) : Boolean{
        return withContext(Dispatchers.IO)
        {
            try {
                deleteFile(filename)
            } catch (e : Exception)
            {
                Toast.makeText(parent, e.printStackTrace().toString(), Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    private suspend fun loadPicturesFromInternalStorage() : List<InternalStoragePicture> {
        return withContext(Dispatchers.IO)
        {
            val files = filesDir.listFiles()
            files?.filter { it.canRead() && it.isFile && (it.name.endsWith(".jpg") || it.name.endsWith(".jpeg")) }?.map {
                val bytes = it.readBytes()
                val bmp = BitmapFactory.decodeByteArray(bytes, 0,bytes.size)
                InternalStoragePicture(it.name, bmp)
            }?: listOf()
        }
    }

    private suspend fun savePictureToInternalStorage(filename : String, bmp : Bitmap): Boolean
    {
        return withContext(Dispatchers.IO){
            try {
                openFileOutput("$filename.jpg", MODE_PRIVATE).use { stream ->
                    if(!bmp.compress(Bitmap.CompressFormat.JPEG, 100, stream))
                    {
                        throw IOException("Couldn't save this!")
                    }
                }
                true
            }catch (e : IOException)
            {
                Toast.makeText(parent, e.printStackTrace().toString(), Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(contentObserver)
    }
}