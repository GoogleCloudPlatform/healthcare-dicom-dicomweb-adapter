package com.github.jaiimageio.jpeg2000;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;
import java.util.Random;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.junit.Before;
import org.junit.Test;

import com.github.jaiimageio.jpeg2000.J2KImageWriteParam;

/**
 * Test JPEG2000 writing
 * 
 */
public class Jpeg2000WriteTest {

    private static final int SIZE = 1024;
	private final BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
    
    @Before
    public void randomize() {
    	Random r = new Random(1337);
    	for (int x=0; x<SIZE; x++) {
    		for (int y=0; y<SIZE; y++) {
    			image.setRGB(x, y, r.nextInt(0xffffff) ^ 0x00000000); // 100% alpha
    		}
		}
    }
    

    @Test
    public void lossless() throws Exception {
        File f = File.createTempFile("test-jpeg2000-lossless", ".jp2");
        f.deleteOnExit();        
        Iterator<ImageWriter> writers = ImageIO.getImageWritersBySuffix("jp2");
        assertTrue(writers.hasNext());
        ImageWriter writer = writers.next();
        J2KImageWriteParam writeParams = (J2KImageWriteParam) writer.getDefaultWriteParam();
        writeParams.setLossless(true);
//        writeParams.setFilter(J2KImageWriteParam.FILTER_53);
//        writeParams.setEncodingRate(64.0f);
//        writeParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
//        writeParams.setCompressionType("JPEG2000");
//        writeParams.setCompressionQuality(1.0f);
        
        ImageOutputStream ios = ImageIO.createImageOutputStream(f);
        writer.setOutput(ios);
        writer.write(null, new IIOImage(image, null, null), writeParams);
        writer.dispose();
        ios.close();
        assertTrue("Expected file size > 128kB", f.length() > 128*1024);
        //System.out.println(f.length());
        BufferedImage read = ImageIO.read(f);
        assertEquals(SIZE, read.getWidth());
    }

    
    @Test
    public void lossyWrite() throws Exception {
        File f = File.createTempFile("test-jpeg2000-lossy", ".jp2");
        f.deleteOnExit();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersBySuffix("jp2");
        assertTrue(writers.hasNext());
        ImageWriter writer = writers.next();
        J2KImageWriteParam writeParams = (J2KImageWriteParam) writer.getDefaultWriteParam();
        writeParams.setLossless(false);
        writeParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        writeParams.setCompressionType("JPEG2000");
//        writeParams.setFilter(J2KImageWriteParam.FILTER_97);


        
        writeParams.setCompressionQuality(0.0f);
        writeParams.setEncodingRate(0.5f);
        ImageOutputStream ios = ImageIO.createImageOutputStream(f);
        writer.setOutput(ios);
        writer.write(null, new IIOImage(image, null, null), writeParams);
        writer.dispose();
        ios.close();
        assertTrue("Expected file size < 1MB", f.length() < 128*1024);
        //System.out.println(f.length());
        BufferedImage read = ImageIO.read(f);
        assertEquals(SIZE, read.getWidth());
    }
    
}
