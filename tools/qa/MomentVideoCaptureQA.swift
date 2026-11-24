import UIKit
import ComposeApp
import UniformTypeIdentifiers

enum ChatMediaError:Error {case invalid}

/// Runs only in a disposable simulator bundle. A simulator has no video camera.
@main final class CaptureQA:UIResponder,UIApplicationDelegate {
    var window:UIWindow?
    func application(_ application:UIApplication,didFinishLaunchingWithOptions options:[UIApplication.LaunchOptionsKey:Any]?)->Bool {
        window=UIWindow(frame:UIScreen.main.bounds)
        window?.rootViewController=UIViewController();window?.makeKeyAndVisible()
        DispatchQueue.main.async {self.check()}
        return true
    }
    func check() {
        let picker=IosMomentVideoPicker()
        var calls=0
        let available=UIImagePickerController.isSourceTypeAvailable(.camera) &&
            UIImagePickerController.availableMediaTypes(for:.camera)?.contains(UTType.movie.identifier)==true
        var valid = !available
        var errors:[String]=[]
        // The second request must not get stuck behind the failed first request.
        for _ in 0..<2 {
            picker.record {result in
                calls+=1
                errors.append(result.error ?? "No error")
                valid = valid && result.video==nil && result.error?.contains("isn't available")==true
            }
        }
        picker.cancel();picker.close()
        let report:[String:Any]=["passed":valid && calls==2,"camera_unavailable_retry":valid,"camera_available":available,"errors":errors,"callbacks":calls,"physical_capture_verified":false]
        let documents=FileManager.default.urls(for:.documentDirectory,in:.userDomainMask)[0]
        if let data=try? JSONSerialization.data(withJSONObject:report,options:.prettyPrinted) {
            try? data.write(to:documents.appendingPathComponent("result.json"))
        }
    }
}
