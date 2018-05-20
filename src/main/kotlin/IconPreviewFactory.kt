import com.android.ide.common.resources.ResourceResolver
import com.android.internal.util.XmlUtils
import com.android.resources.ResourceUrl
import com.android.tools.adtui.ImageUtils
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.gradle.structure.configurables.ui.UiUtil
import com.android.tools.idea.rendering.GutterIconFactory
import com.android.tools.idea.res.ResourceHelper
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.ui.UIUtil
import drawables.Drawable
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.awt.Component
import java.awt.Graphics
import java.awt.Image
import java.awt.image.BufferedImage
import java.awt.image.ImageObserver
import java.io.File
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory



class IconPreviewFactory {

    companion object {
        private const val IMAGE_SIZE = 16
        private const val XML_TYPE = ".xml"
        private const val DRAWABLES_FOLDER_TYPE = "drawable"

        var psiManager: PsiManager? = null
        private set

        fun createIcon(element: PsiElement): Icon? {
            var result: Icon? = null
            if (element is PsiFile) {
                psiManager = element.manager
                val module = ProjectRootManager.getInstance(element.project).fileIndex.getModuleForFile(element.virtualFile)
                module?.let {
                    val configuration = ConfigurationManager.getOrCreateInstance(it).getConfiguration(element.virtualFile)
                    val resourceResolver = configuration.resourceResolver
                    result = GutterIconFactory.createIcon(element.virtualFile.path, resourceResolver, IMAGE_SIZE, IMAGE_SIZE)
                            ?: handleElement(element.virtualFile, resourceResolver)
                }
            }
            psiManager = null
            return result
        }

        private fun handleElement(virtualFile: VirtualFile, resolver: ResourceResolver?): Icon? {
            val path = virtualFile.path
            return if (path.endsWith(XML_TYPE) && path.contains(DRAWABLES_FOLDER_TYPE)) {
                createXmlIcon(virtualFile, resolver)
            } else null
        }

        private fun createXmlIcon(virtualFile: VirtualFile, resolver: ResourceResolver?): Icon? {
            val documentBuilderFactory = DocumentBuilderFactory.newInstance()
            val documentBuilder = documentBuilderFactory.newDocumentBuilder()
            val document = documentBuilder.parse(File(virtualFile.path))

            val root = document.documentElement ?: return null

            if (resolver != null) {
                replaceResourceReferences(root, resolver)
            }

            val drawable = DrawableInflater.getDrawable(root)
            drawable?.let {
                val image = UIUtil.createImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB)
                drawable.draw(image)

                if (UIUtil.isRetina()) {
                    val retinaIcon = getRetinaIcon(image)
                    if (retinaIcon != null) {
                        return retinaIcon
                    }
                }

                return ImageIcon(image)
            }


            return null
        }

        private fun getRetinaIcon(image: BufferedImage): RetinaImageIcon? {
            if (UIUtil.isRetina()) {
                val hdpiImage = ImageUtils.convertToRetina(image)
                if (hdpiImage != null) {
                    return RetinaImageIcon(hdpiImage)
                }
            }

            return null
        }

        private fun replaceResourceReferences(node: Node, resolver: ResourceResolver) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                val element = node as Element
                val attributes = element.attributes

                var i = 0
                val n = attributes.length
                while (i < n) {
                    val attribute = attributes.item(i)
                    val value = attribute.nodeValue
                    if (isReference(value)) {
                        val resolvedValue = ResourceHelper.resolveStringValue(resolver, value)
                        if (!isReference(resolvedValue)) {
                            attribute.nodeValue = resolvedValue
                        }
                    }
                    ++i
                }
            }

            var newNode = node.firstChild
            while (newNode != null) {
                replaceResourceReferences(newNode, resolver)
                newNode = newNode.nextSibling
            }
        }

        private fun isReference(attributeValue: String): Boolean {
            return ResourceUrl.parse(attributeValue) != null
        }
    }

    private class RetinaImageIcon constructor(image: Image) : ImageIcon(image, "") {
        @Synchronized
        override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
            UIUtil.drawImage(g, this.image, x, y, null as ImageObserver?)
        }
    }
}