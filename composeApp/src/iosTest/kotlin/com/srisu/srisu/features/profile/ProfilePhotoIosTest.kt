package com.srisu.srisu.features.profile

import com.srisu.srisu.utils.prepareProfilePhoto
import kotlinx.cinterop.*
import kotlinx.coroutines.runBlocking
import platform.CoreGraphics.*
import platform.Foundation.*
import platform.UIKit.*
import kotlin.test.*

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class ProfilePhotoIosTest {
    @Test fun heicAndExifRotationAreDecodedByNativeImageIO() = runBlocking {
        val fixtures = listOf(
            Triple("jpg", "/9j/4AAQSkZJRgABAQAAAQABAAD/4QAiRXhpZgAATU0AKgAAAAgAAQESAAMAAAABAAYAAAAAAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCABQAHgDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDEooor3TsCiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKACiiigAooooAKKKKAP/9k=", 80.0 to 120.0),
            Triple("heic", "AAAAJGZ0eXBoZWljAAAAAG1pZjFNaVBybWlhZk1pSEJoZWljAAABhW1ldGEAAAAAAAAAIWhkbHIAAAAAAAAAAHBpY3QAAAAAAAAAAAAAAAAAAAAAJGRpbmYAAAAcZHJlZgAAAAAAAAABAAAADHVybCAAAAABAAAADnBpdG0AAAAAAAEAAAAjaWluZgAAAAAAAQAAABVpbmZlAgAAAAABAABodmMxAAAAAOVpcHJwAAAAxGlwY28AAAATY29scm5jbHgAAgACAAaAAAAADGNsbGkAywBAAAAAFGlzcGUAAAAAAAAAeAAAAFAAAAAJaXJvdAAAAAAQcGl4aQAAAAADCAgIAAAAcGh2Y0MBA3AAAACwAAAAAAAe8AD8/fj4AAALA6AAAQAXQAEMAf//A3AAAAMAsAAAAwAAAwAecCShAAEAIkIBAQNwAAADALAAAAMAAAMAHqAUIFHCviHuRZVNwICBgCCiAAEACUQBwGFyyEBTJAAAABlpcG1hAAAAAAAAAAEAAQaBAgMFhoQAAAAeaWxvYwAAAABEAAABAAEAAAABAAABuQAAADkAAAABbWRhdAAAAAAAAABJAAAANSgBr7Lw8EaBfP/7756/0C+y4Ho+xb9nmsaJ5bnf+x7f+K/QPm3F/Oc2s/QiuUg4rwp0m7Bb", 120.0 to 80.0))
        for ((suffix, encoded, dimensions) in fixtures) {
            val bytes = kotlin.io.encoding.Base64.decode(encoded)
            val path = NSTemporaryDirectory() + "synthetic-profile.$suffix"
            bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()).writeToFile(path, true) }
            try {
                val photo = assertNotNull(prepareProfilePhoto(path))
                val prepared = assertNotNull(photo.fileBytes)
                val data = prepared.usePinned { NSData.create(bytes = it.addressOf(0), length = prepared.size.toULong()) }
                assertNotNull(UIImage.imageWithData(data)).size.useContents {
                    assertEquals(dimensions.first, width); assertEquals(dimensions.second, height)
                }
            } finally { NSFileManager.defaultManager.removeItemAtPath(path, null) }
        }
    }
    @Test fun cameraOriginalDownsamplesAndRejectsInvalidFiles() = runBlocking {
        val path = NSTemporaryDirectory() + "synthetic-profile.jpg"
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(2400.0, 1800.0), true, 1.0)
        UIColor.purpleColor.setFill()
        UIRectFill(CGRectMake(0.0, 0.0, 2400.0, 1800.0))
        val image = assertNotNull(UIGraphicsGetImageFromCurrentImageContext())
        UIGraphicsEndImageContext()
        assertTrue(assertNotNull(UIImageJPEGRepresentation(image, .95)).writeToFile(path, true))
        try {
            val prepared = assertNotNull(prepareProfilePhoto(path))
            assertEquals("image/jpeg", prepared.mimeType)
            val bytes = assertNotNull(prepared.fileBytes)
            assertTrue(bytes.size < 5 * 1024 * 1024)
            val jpeg = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
            val output = assertNotNull(UIImage.imageWithData(jpeg))
            output.size.useContents { assertEquals(1600.0, width); assertEquals(1200.0, height) }
            assertNull(prepareProfilePhoto(path + ".missing"))
        } finally { NSFileManager.defaultManager.removeItemAtPath(path, null) }
    }
}
