// Preserve the supplied central artwork and glows. Crop only the outer tile rim, extend its inner background, and add adaptive-mask padding.
// Run: swift -module-cache-path /tmp/delta-swift-cache ci/restore-launcher-art.swift .
import Foundation
import AppKit
import CoreGraphics
let root = URL(fileURLWithPath: CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : ".")
let directory = root.appendingPathComponent("app/src/main/res/drawable-nodpi")
let space = CGColorSpace(name:CGColorSpace.sRGB)!
for index in 1...9 {
    let sourceURL=directory.appendingPathComponent(String(format:"app_icon_%02d.png",index))
    let rep=NSBitmapImageRep(data:try Data(contentsOf:sourceURL))!
    let image=rep.cgImage!, width=image.width, height=image.height
    var source=[UInt8](repeating:0,count:width*height*4)
    source.withUnsafeMutableBytes { bytes in
        let context=CGContext(data:bytes.baseAddress,width:width,height:height,bitsPerComponent:8,bytesPerRow:width*4,space:space,bitmapInfo:CGImageAlphaInfo.premultipliedLast.rawValue)!
        context.draw(image,in:CGRect(x:0,y:0,width:width,height:height))
    }
    var pixels=[UInt8](repeating:255,count:720*720*4)
    let side=Double(width), margin=56.0*side/512, radius=96.0*side/512
    for y in 0..<720 { for x in 0..<720 {
        var sx=(Double(x)+0.5-144)/432*side-0.5
        var sy=(Double(y)+0.5-144)/432*side-0.5
        sx=min(max(sx,margin),side-margin-1);sy=min(max(sy,margin),side-margin-1)
        // Extend the blue icons along a rounded contour so their corner glow cannot form a square patch.
        let cx=min(max(sx,margin+radius),side-margin-radius-1)
        let cy=min(max(sy,margin+radius),side-margin-radius-1)
        let dx=sx-cx, dy=sy-cy, distance=hypot(dx,dy)
        if index>=6 && distance>radius {sx=cx+dx*radius/distance;sy=cy+dy*radius/distance}
        let ix=Int(sx), iy=Int(sy), fx=sx-Double(ix), fy=sy-Double(iy)
        for channel in 0..<3 {
            func sample(_ dx:Int,_ dy:Int)->Double {Double(source[(min(iy+dy,height-1)*width+min(ix+dx,width-1))*4+channel])}
            let value=(sample(0,0)*(1-fx)+sample(1,0)*fx)*(1-fy)+(sample(0,1)*(1-fx)+sample(1,1)*fx)*fy
            pixels[(y*720+x)*4+channel]=UInt8(min(255,max(0,value.rounded())))
        }
    }}
    let png:Data = pixels.withUnsafeMutableBytes { bytes in
        let context=CGContext(data:bytes.baseAddress,width:720,height:720,bitsPerComponent:8,bytesPerRow:720*4,space:space,bitmapInfo:CGImageAlphaInfo.premultipliedLast.rawValue)!
        return NSBitmapImageRep(cgImage:context.makeImage()!).representation(using:.png,properties:[:])!
    }
    let name=String(format:"launcher_art_%02d.png",index)
    try png.write(to:directory.appendingPathComponent(name));print(name)
}
