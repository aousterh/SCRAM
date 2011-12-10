/*
 * ContentParser.java
 * 
 * Provides methods for converting between strings and the content of
 * messages in a specific XML format.
 * 
 * The format used is:
 * <?xml version="1.0" encoding="UTF-8"?>
 * <content>
 *     <message> MESSAGE_HERE </message>
 *     <signature> SIGNATURE_HERE </signature>
 * </content>
 * 
 * Resources used to generate this code:
 * http://tutorials.jenkov.com/java-xml/dom-document-object.html
 * http://www.mkyong.com/java/how-to-create-xml-file-in-java-dom/
 * 
 */

package org.haggle.Micropublisher;

import java.io.File;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.os.Environment;
import android.util.Log;

public class ContentParser {
	
	
	public static void parseContent(String filepath) {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		Log.d(Micropublisher.LOG_TAG, "parsing!!");
		try {
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(new File(filepath));
			Log.d(Micropublisher.LOG_TAG, "doc: " + document);
			Element root = document.getDocumentElement();
			NodeList nodes = root.getChildNodes();
			Log.d(Micropublisher.LOG_TAG, "num nodes: " + nodes.getLength());
			for (int i = 0; i < nodes.getLength(); i++) {
				Node node = nodes.item(i);
				Log.d(Micropublisher.LOG_TAG, node.getNodeName() + " " + node.getTextContent());
			}
										    
		} catch (Exception e) {
			Log.d(Micropublisher.LOG_TAG, "exception: " + e.getMessage());
		}

	}
	
	public static String createFile(String message, String signature) {
		Log.d(Micropublisher.LOG_TAG, "creating!!");
		String filepath = null;
		
		try {
			// create document
			DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = builderFactory.newDocumentBuilder();
			Document document = builder.newDocument();
			
			// add root element
			Element rootElement = document.createElement("content");
			document.appendChild(rootElement);
			
			// add child elements
			Element messageElement = document.createElement("message");
			rootElement.appendChild(messageElement);
			messageElement.appendChild(document.createTextNode(message));
			
			Element signatureElement = document.createElement("signature");
			rootElement.appendChild(signatureElement);
			signatureElement.appendChild(document.createTextNode(signature));
			
			// create DOM source
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(document);
			
			// create a file and write to it
			File dir = new File(Environment.getExternalStorageDirectory() + "/Micropublisher");
    		String filename = "micropublisher-" + System.currentTimeMillis();
    		filepath = dir + "/" + filename;
    		File generatedFile = new File(filepath);
			StreamResult result = new StreamResult(generatedFile);
			transformer.transform(source, result);
			
			Log.d(Micropublisher.LOG_TAG, "survived");
			
			
		} catch (Exception e) {
			Log.d(Micropublisher.LOG_TAG, "error generating XML");
		}
		return filepath;
	}
	
}