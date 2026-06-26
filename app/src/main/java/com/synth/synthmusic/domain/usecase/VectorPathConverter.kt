package com.synth.synthmusic.domain.usecase

import android.graphics.Matrix
import android.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.core.graphics.PathParser

/**
 * Converts a Compose [ImageVector] into an [android.graphics.Path].
 *
 * This lets the artwork generator draw the same Material icon that is shown
 * in the picker UI, instead of relying on hand-written approximations.
 *
 * Group transforms (rotation, scale, translation) are recursively applied so
 * multi-group vector assets are handled correctly.
 */
fun ImageVector.toAndroidPath(): Path {
    val result = Path()
    addGroup(root, Matrix(), result)
    return result
}

private fun addGroup(
    group: VectorGroup,
    parentMatrix: Matrix,
    out: Path
) {
    // VectorGroup applies transforms in the order: scale, rotate, translate.
    val groupMatrix = Matrix().apply {
        postScale(group.scaleX, group.scaleY, group.pivotX, group.pivotY)
        postRotate(group.rotation, group.pivotX, group.pivotY)
        postTranslate(group.translationX, group.translationY)
    }

    val combined = Matrix(parentMatrix)
    combined.postConcat(groupMatrix)

    group.forEach { node: VectorNode ->
        when (node) {
            is VectorPath -> {
                val pathData = node.pathData.toPathData()
                val path = PathParser.createPathFromPathData(pathData)
                path.transform(combined)
                out.addPath(path)
            }
            is VectorGroup -> addGroup(node, combined, out)
        }
    }
}

/**
 * Builds an SVG-style path data string from a list of [PathNode]s.
 */
private fun List<PathNode>.toPathData(): String {
    return buildString {
        this@toPathData.forEach { node ->
            when (node) {
                is PathNode.Close -> append("Z")
                is PathNode.MoveTo -> append("M ${node.x} ${node.y}")
                is PathNode.RelativeMoveTo -> append("m ${node.dx} ${node.dy}")
                is PathNode.LineTo -> append("L ${node.x} ${node.y}")
                is PathNode.RelativeLineTo -> append("l ${node.dx} ${node.dy}")
                is PathNode.HorizontalTo -> append("H ${node.x}")
                is PathNode.RelativeHorizontalTo -> append("h ${node.dx}")
                is PathNode.VerticalTo -> append("V ${node.y}")
                is PathNode.RelativeVerticalTo -> append("v ${node.dy}")
                is PathNode.CurveTo -> append(
                    "C ${node.x1} ${node.y1} ${node.x2} ${node.y2} ${node.x3} ${node.y3}"
                )
                is PathNode.RelativeCurveTo -> append(
                    "c ${node.dx1} ${node.dy1} ${node.dx2} ${node.dy2} ${node.dx3} ${node.dy3}"
                )
                is PathNode.ReflectiveCurveTo -> append(
                    "S ${node.x1} ${node.y1} ${node.x2} ${node.y2}"
                )
                is PathNode.RelativeReflectiveCurveTo -> append(
                    "s ${node.dx1} ${node.dy1} ${node.dx2} ${node.dy2}"
                )
                is PathNode.QuadTo -> append(
                    "Q ${node.x1} ${node.y1} ${node.x2} ${node.y2}"
                )
                is PathNode.RelativeQuadTo -> append(
                    "q ${node.dx1} ${node.dy1} ${node.dx2} ${node.dy2}"
                )
                is PathNode.ReflectiveQuadTo -> append("T ${node.x} ${node.y}")
                is PathNode.RelativeReflectiveQuadTo -> append("t ${node.dx} ${node.dy}")
                is PathNode.ArcTo -> append(
                    "A ${node.horizontalEllipseRadius} ${node.verticalEllipseRadius} " +
                        "${node.theta} ${node.isMoreThanHalf.toFlag()} " +
                        "${node.isPositiveArc.toFlag()} ${node.arcStartX} ${node.arcStartY}"
                )
                is PathNode.RelativeArcTo -> append(
                    "a ${node.horizontalEllipseRadius} ${node.verticalEllipseRadius} " +
                        "${node.theta} ${node.isMoreThanHalf.toFlag()} " +
                        "${node.isPositiveArc.toFlag()} ${node.arcStartDx} ${node.arcStartDy}"
                )
            }
            append(" ")
        }
    }
}

private fun Boolean.toFlag(): Int = if (this) 1 else 0
