import qupath.lib.objects.PathObjects
import qupath.lib.regions.ImagePlane
import qupath.lib.roi.ROIs
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

// 1. Setup Image and Server configuration
def imageData = getCurrentImageData()
def server = imageData.getServer()
def path = "http://anton:8001/predict" // Docker container endpoint

// Determine region to send (uses selected annotation, otherwise full image viewer region)
def roi = getSelectedROI()
if (roi == null) {
    println("No region selected. Exporting the entire current view...")
    def viewer = getCurrentViewer()
    roi = viewer.getROI()
}

int x0 = (int) roi.getBoundsX()
int y0 = (int) roi.getBoundsY()
int w = (int) roi.getBoundsWidth()
int h = (int) roi.getBoundsHeight()
int maxSize = 640

def annotationsToAdd = []
def plane = ImagePlane.getDefaultPlane()

def parsePredictions = { String responseText ->
    def json = [predictions: []]
    responseText.findAll(/\{[^}]*\}/).each { obj ->
        def map = [:]
        obj.findAll(/"([^\"]+)":\s*("[^"]*"|[0-9]+(?:\.[0-9]+)?)/) { _, key, value ->
            if (value.startsWith('"')) {
                map[key] = value[1..-2]
            } else if (value.contains('.')) {
                map[key] = value.toDouble()
            } else {
                map[key] = value.toInteger()
            }
        }
        if (map.containsKey('class_name')) {
            json.predictions << map
        }
    }
    return json
}

def sendTile = { int tx, int ty, int tileW, int tileH ->
    def tileRoi = ROIs.createRectangleROI(tx, ty, tileW, tileH, plane)
    def request = RegionRequest.createInstance(server.getPath(), 1.0, tileRoi)
    def img = server.readBufferedImage(request)
    def baos = new ByteArrayOutputStream()
    ImageIO.write(img, "jpg", baos)
    byte[] imageBytes = baos.toByteArray()

    def boundary = "---QuPathBoundary---"
    def url = new URL(path)
    def connection = (HttpURLConnection) url.openConnection()
    connection.setDoOutput(true)
    connection.setRequestMethod("POST")
    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)

    def outputStream = connection.getOutputStream()
    def writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"), true)
    writer.append("--" + boundary).append("\r\n")
    writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"tile-${tx}-${ty}.jpg\"").append("\r\n")
    writer.append("Content-Type: image/jpeg").append("\r\n\r\n")
    writer.flush()
    outputStream.write(imageBytes)
    outputStream.flush()
    writer.append("\r\n").append("--" + boundary + "--").append("\r\n")
    writer.close()

    return connection
}

for (int ty = y0; ty < y0 + h; ty += maxSize) {
    int tileH = Math.min(maxSize, y0 + h - ty)
    for (int tx = x0; tx < x0 + w; tx += maxSize) {
        int tileW = Math.min(maxSize, x0 + w - tx)
        def connection = sendTile(tx, ty, tileW, tileH)

        if (connection.getResponseCode() == 200) {
            def responseText = connection.getInputStream().getText()
            def json = parsePredictions(responseText)

            json.predictions.each { pred ->
                double globalX = tx + pred.x
                double globalY = ty + pred.y
                double width = pred.width
                double height = pred.height
                def qpRoi = ROIs.createRectangleROI(globalX, globalY, width, height, plane)
                def annotation = PathObjects.createAnnotationObject(qpRoi)
                annotation.setName(pred.class_name)
                annotation.setPathClass(getPathClass(pred.class_name))
                annotationsToAdd.add(annotation)
            }
        } else {
            println("Error calling Docker API for tile ${tx},${ty}: " + connection.getResponseCode())
        }
    }
}

addObjects(annotationsToAdd)
println("Successfully imported " + annotationsToAdd.size() + " bounding boxes from YOLO!")
