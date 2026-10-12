package com.cagri.real.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bounded app-owned square JPEG; no originals, location EXIF or gallery-wide permission retained. */
object ProfilePhotoStorage {
    const val MAX_EDGE=512
    const val MAX_BYTES=192*1024
    const val KEY_KIND="avatar_image_kind"
    suspend fun prepare(context:Context,uri:Uri):File=withContext(Dispatchers.IO) {
        require(uri.scheme=="content")
        val source=File(context.cacheDir,"profile_source_${UUID.randomUUID()}")
        val result=File(context.cacheDir,"profile_preview_${UUID.randomUUID()}.jpg")
        var decoded:Bitmap?=null;var oriented:Bitmap?=null;var square:Bitmap?=null;var scaled:Bitmap?=null
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> source.outputStream().use { output ->
                val buffer=ByteArray(32*1024);var total=0
                while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=20*1024*1024){"Fotoğraf en fazla 20 MB olabilir."};output.write(buffer,0,n)}
            }} ?: error("Fotoğraf açılamadı.")
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(source.path,bounds)
            require(bounds.outWidth>0 && bounds.outHeight>0 && bounds.outWidth.toLong()*bounds.outHeight<=80_000_000L){"Fotoğraf boyutu desteklenmiyor."}
            var sample=1
            while(minOf(bounds.outWidth,bounds.outHeight)/sample>1024 || (bounds.outWidth.toLong()/sample)*(bounds.outHeight/sample)>4_000_000L)sample*=2
            decoded=BitmapFactory.decodeFile(source.path,BitmapFactory.Options().apply{inSampleSize=sample}) ?: error("Fotoğraf okunamadı.")
            val original=decoded!!
            val matrix=Matrix()
            val orientation=runCatching{ExifInterface(source.path).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            when(orientation){
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL->matrix.setScale(-1f,1f)
                ExifInterface.ORIENTATION_ROTATE_180->matrix.setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL->matrix.setScale(1f,-1f)
                ExifInterface.ORIENTATION_TRANSPOSE->{matrix.setRotate(90f);matrix.postScale(-1f,1f)}
                ExifInterface.ORIENTATION_ROTATE_90->matrix.setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE->{matrix.setRotate(-90f);matrix.postScale(-1f,1f)}
                ExifInterface.ORIENTATION_ROTATE_270->matrix.setRotate(-90f)
            }
            oriented=Bitmap.createBitmap(original,0,0,original.width,original.height,matrix,true)
            val image=oriented!!;val edge=minOf(image.width,image.height)
            square=Bitmap.createBitmap(image,(image.width-edge)/2,(image.height-edge)/2,edge,edge)
            val outputEdge=minOf(edge,MAX_EDGE)
            scaled=Bitmap.createScaledBitmap(square!!,outputEdge,outputEdge,true)
            var quality=90
            do {
                result.outputStream().use{check(scaled!!.compress(Bitmap.CompressFormat.JPEG,quality,it))}
                quality-=10
            }while(result.length()>MAX_BYTES && quality>=40)
            require(result.length() in 1..MAX_BYTES.toLong()){ "Fotoğraf küçültülemedi." }
            result
        }catch(e:Exception){result.delete();throw e}
        finally{
            source.delete()
            listOfNotNull(decoded,oriented,square,scaled).distinct().forEach{it.recycle()}
        }
    }
    suspend fun save(context:Context,preview:File,owner:String?)=withContext(Dispatchers.IO) {
        require(preview.isFile && preview.length() in 1..MAX_BYTES.toLong())
        ProfileAccountStore.save(context,owner,"photo","jpg") { target -> preview.copyTo(target) }
    }
}
