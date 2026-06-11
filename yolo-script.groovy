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

// Extract the bounding coordinates of the area we want to send
int x = (int) roi.getBoundsX()
int y = (int) roi.getBoundsY()
int w = (int) roi.getBoundsWidth()
int h = (int) roi.getBoundsHeight()

// 2. Request pixels from QuPath and write to a byte array
def request = RegionRequest.createInstance(server.getPath(), 1.0, roi)
def img = server.readBufferedImage(request)

def baos = new ByteArrayOutputStream()
ImageIO.write(img, "jpg", baos)
byte[] imageBytes = baos.toByteArray()

// 3. Send Multi-part HTTP POST request to Docker
def boundary = "---QuPathBoundary---"
def url = new URL(path)
def connection = (HttpURLConnection) url.openConnection()
connection.setDoOutput(true)
connection.setRequestMethod("POST")
connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)

def outputStream = connection.getOutputStream()
def writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"), true)

writer.append("--" + boundary).append("\r\n")
writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"region.jpg\"").append("\r\n")
writer.append("Content-Type: image/jpeg").append("\r\n\r\n")
writer.flush()

outputStream.write(imageBytes)
outputStream.flush()

writer.append("\r\n").append("--" + boundary + "--").append("\r\n")
writer.close()

// 4. Parse the JSON Response from Docker
if (connection.getResponseCode() == 200) {
    def responseText = connection.getInputStream().getText()
    def json = [predictions: []]
    
    // Simple parser for the expected YOLO response structure
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
    
    def annotationsToAdd = []
    def plane = ImagePlane.getDefaultPlane()

    // 5. Convert YOLO JSON response to Native QuPath Objects
    json.predictions.each { pred ->
        // Adjust the local YOLO coordinates relative to QuPath's global canvas coordinates
        double globalX = x + pred.x
        double globalY = y + pred.y
        double width = pred.width
        double height = pred.height
        
        // Create QuPath Rectangle ROI
        def qpRoi = ROIs.createRectangleROI(globalX, globalY, width, height, plane)
        def annotation = PathObjects.createAnnotationObject(qpRoi)
        
        // Assign Class Name and classification
        annotation.setName(pred.class_name)
        annotation.setPathClass(getPathClass(pred.class_name))
        
        // Skip adding extra result details; just create the bounding box
        annotationsToAdd.add(annotation)
    }
    
    // Add annotations safely to QuPath GUI
    addObjects(annotationsToAdd)
    println("Successfully imported " + annotationsToAdd.size() + " bounding boxes from YOLO!")
} else {
    println("Error calling Docker API: " + connection.getResponseCode())
}