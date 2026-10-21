package com.cagri.real.ui.pano

import androidx.compose.foundation.gestures.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import com.cagri.real.boards.*
import kotlin.math.*

fun viewportAfterGesture(v:BoardViewport,centroid:Offset,pan:Offset,zoomChange:Float):BoardViewport {
    val zoom=(v.zoom*zoomChange).coerceIn(BOARD_MIN_ZOOM,BOARD_MAX_ZOOM);
    val ratio=zoom/v.zoom
    return BoardViewport(centroid.x-(centroid.x-v.x)*ratio+pan.x,centroid.y-(centroid.y-v.y)*ratio+pan.y,zoom).clamped()
}
fun boardPoint(screenDp:Offset,v:BoardViewport)=Offset((screenDp.x-v.x)/v.zoom,(screenDp.y-v.y)/v.zoom)
fun blockHit(point:Offset,t:BoardTransform,margin:Float=0f):Boolean {
    val a=-t.rotation*PI.toFloat()/180f;
    val dx=point.x-t.x;
    val dy=point.y-t.y
    val x=dx*cos(a)-dy*sin(a);
    val y=dx*sin(a)+dy*cos(a)
    return x in -margin..(t.width+margin)&&y in -margin..(t.height+margin)
}
fun rotatedDrag(delta:Offset,rotation:Float):Offset {val a=rotation*PI.toFloat()/180f;
    return Offset(delta.x*cos(a)-delta.y*sin(a),delta.x*sin(a)+delta.y*cos(a))}
/** Compose has already inverted the parent zoom; only density and rotation remain. */
fun boardLocalDragToWorld(localPixels: Offset, density: Float, rotation: Float): Offset =
    rotatedDrag(localPixels / density, rotation)
/** Positive screen-space angle is clockwise, matching graphicsLayer.rotationZ. */
fun boardRotationDelta(before: Offset, after: Offset): Float {
    if (before.getDistance() < 1f || after.getDistance() < 1f) return 0f
    return normalizedBoardRotation((atan2(after.y,after.x)-atan2(before.y,before.x))*180f/PI.toFloat())
}
/** Parent intercepts two fingers before children; one-finger block controls own their gestures. */
fun Modifier.boardViewportGestures(
    density:Float,viewport:()->BoardViewport,items:()->List<PanoItem>,onChange:(BoardViewport)->Unit,onCommit:(BoardViewport)->Unit,onEmptyTap:()->Unit,controlHit:(Offset)->Boolean={false}
)=pointerInput(density) {
    awaitEachGesture {
        val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
        val point=boardPoint(down.position/density,viewport())
        val empty=!controlHit(point) && items().none {blockHit(point,it.transform)}
        var changed=false;
        var panStarted=false;
        var total=Offset.Zero
        do {
            val event=awaitPointerEvent(PointerEventPass.Initial);
            val count=event.changes.count {it.pressed}
            if(count>=2){val pan=event.calculatePan()/density;
                val centroid=event.calculateCentroid(useCurrent=false)/density;
                val zoom=event.calculateZoom()
                if(centroid.x.isFinite()&&centroid.y.isFinite()){onChange(viewportAfterGesture(viewport(),centroid,pan,zoom));
                    changed=true};
                event.changes.forEach {it.consume()}
            }else if(empty&&count==1){val delta=event.changes.first {it.pressed}.let {it.position-it.previousPosition};
                total+=delta
                if(total.getDistance()>viewConfiguration.touchSlop)panStarted=true
                if(panStarted){val v=viewport();
                    onChange(v.copy(x=v.x+delta.x/density,y=v.y+delta.y/density).clamped());
                    changed=true;
                    event.changes.forEach{it.consume()}}
            }
        }while(event.changes.any {it.pressed})
        if(changed)onCommit(viewport())else if(empty)onEmptyTap()
    }
}
