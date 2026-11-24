import UIKit
import ComposeApp

enum ChatMediaError: Error {case invalid}

@main final class VideoQA: UIResponder, UIApplicationDelegate {
    var window:UIWindow?
    var native:IosMomentVideo?
    var latest:MomentPlaybackEvent?
    var firstFrameAt:Date?
    var started=Date()
    var paused=false
    var resumed=false
    var done=false
    var source:MomentPlayback?
    var root:UIViewController!
    let documents=FileManager.default.urls(for:.documentDirectory,in:.userDomainMask)[0]
    func application(_ application:UIApplication,didFinishLaunchingWithOptions options:[UIApplication.LaunchOptionsKey:Any]?)->Bool {
        window=UIWindow(frame:UIScreen.main.bounds);root=UIViewController();root.view.backgroundColor = .black
        window?.rootViewController=root;window?.makeKeyAndVisible()
        Task {await begin()}
        return true
    }
    @MainActor func begin() async {
        do {
            let fixture=try JSONSerialization.jsonObject(with:Data(contentsOf:documents.appendingPathComponent("fixture.json"))) as! [String:Any]
            let origin=fixture["base_url"] as! String
            precondition(URL(string:origin)?.host=="127.0.0.1")
            var request=URLRequest(url:URL(string:origin+"api/social/moment-videos/"+(fixture["video_id"] as! String)+"/playback/")!)
            request.httpMethod="POST";request.httpBody=Data("{}".utf8)
            request.setValue("application/json",forHTTPHeaderField:"Content-Type")
            request.setValue("Bearer "+(fixture["partner_access"] as! String),forHTTPHeaderField:"Authorization")
            let (data,response)=try await URLSession.shared.data(for:request)
            guard (response as? HTTPURLResponse)?.statusCode==200 else {finish(error:"handshake failed");return}
            let access=(try JSONSerialization.jsonObject(with:data) as! [String:Any])["data"] as! [String:Any]
            let playback=MomentPlayback(url:access["url"] as! String,cookieName:access["cookie_name"] as! String,
                cookieValue:access["cookie_value"] as! String,cookiePath:access["cookie_path"] as! String,
                expiresIn:Int32(access["expires_in"] as! Int),durationMs:Int64(access["duration_ms"] as! Int))
            source=playback;started=Date()
            native=IosMomentVideo(source:playback) { [weak self] event in self?.event(event) }
            let controller=native!.controller
            root.addChild(controller);controller.view.frame=root.view.bounds;controller.view.autoresizingMask=[.flexibleWidth,.flexibleHeight]
            root.view.addSubview(controller.view);controller.didMove(toParent:root)
            native?.update(playing:true,muted:true)
            DispatchQueue.main.asyncAfter(deadline:.now()+25) {if !self.done {self.finish(error:"playback timeout")}}
        } catch {finish(error:"fixture setup failed")}
    }
    func event(_ event:MomentPlaybackEvent) {
        latest=event
        if event.firstFrame && firstFrameAt==nil {firstFrameAt=Date()}
        if event.state=="failed" {finish(error:"native playback failed")}
        if event.positionMs>1000 && !paused {
            paused=true;native?.update(playing:false,muted:true)
            DispatchQueue.main.asyncAfter(deadline:.now()+0.4) {
                let at=self.latest?.positionMs ?? 0
                DispatchQueue.main.asyncAfter(deadline:.now()+1.2) {
                    if abs((self.latest?.positionMs ?? 0)-at)>100 {self.finish(error:"paused playback advanced");return}
                    self.resumed=true;self.native?.update(playing:true,muted:true)
                }
            }
        }
        if event.state=="ended" {
            guard firstFrameAt != nil && paused && resumed && event.durationMs>=7500 else {finish(error:"incomplete playback");return}
            finish(error:nil)
        }
    }
    func finish(error:String?) {
        guard !done else {return};done=true
        let report:[String:Any]=["passed":error==nil,"error":error ?? "", "first_frame_ms":Int((firstFrameAt ?? Date()).timeIntervalSince(started)*1000),"duration_ms":latest?.durationMs ?? 0,"pause_resume_end":resumed]
        if let data=try? JSONSerialization.data(withJSONObject:report,options:.prettyPrinted) {try? data.write(to:documents.appendingPathComponent("result.json"))}
        native?.update(playing:false,muted:true)
    }
}
