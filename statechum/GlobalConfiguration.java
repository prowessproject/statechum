/** Copyright (c) 2006, 2007, 2008 Neil Walkinshaw and Kirill Bogdanov

This file is part of StateChum.

statechum is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

StateChum is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with StateChum.  If not, see <http://www.gnu.org/licenses/>.
*/
package statechum;

import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import edu.uci.ics.jung.graph.impl.DirectedSparseGraph;

/**
 * @author kirill
 *
 */
public class GlobalConfiguration {

	public enum ENV_PROPERTIES {
		VIZ_DIR,// the path to visualisation-related information, such as graph layouts and configuration file.
		VIZ_CONFIG,// the configuration file containing window positions and whether to display an assert-related warning.
		VIZ_AUTOFILENAME // used to define a name of a file to load answers to questions
	}

	public enum G_PROPERTIES { // internal properties
		ASSERT,// whether to display assert warning.
		TEMP, // temporary directory to use
		LINEARWARNINGS,// whether to warn when external solver cannot be loaded and we have to fall back to the colt solver.
		BUILDGRAPH, // whether to break if the name of a graph to build is equal to a value of this property
		LOWER, UPPER // window positions (not real properties) to be stored in a configuration file.
		, STOP // used to stop execution - a walkaround re JUnit Eclipse bug on linux amd64.
		,GRAPHICS_MONITOR // the monitor to pop graphs on - useful when using multiple separate screens rather than xinerama or nview
		,TIMEBETWEENHEARTBEATS // How often to check i/o streams and send heartbeat data.
		;
	}

	/**
	 * Default screen to use for any frames created. 
	 */
	public static final int DEFAULT_SCREEN = 0;
	
	/**
	 * Default values of Statechum-wide attributes. 
	 */
	protected static final Map<G_PROPERTIES, String> defaultValues = new TreeMap<G_PROPERTIES, String>();
	static
	{
		defaultValues.put(G_PROPERTIES.LINEARWARNINGS, "false");
		defaultValues.put(G_PROPERTIES.ASSERT, "false");
		defaultValues.put(G_PROPERTIES.GRAPHICS_MONITOR, ""+DEFAULT_SCREEN);
		defaultValues.put(G_PROPERTIES.STOP, "");
		defaultValues.put(G_PROPERTIES.TEMP, "temp");
		defaultValues.put(G_PROPERTIES.TIMEBETWEENHEARTBEATS, "3000");
	}
	
	protected GlobalConfiguration() {}
	
	private static final GlobalConfiguration globalConfiguration = new GlobalConfiguration();
	
	public static GlobalConfiguration getConfiguration()
	{
		return globalConfiguration;
	}
	
	protected Properties properties = null;
	protected Map<String, WindowPosition> windowCoords = null;

	/** Retrieves the name of the property from the property file.
	 *  The first call to this method opens the property file.
	 *  
	 * @param name the name of the property.
	 * @param defaultValue the default value of the property
	 * @return property value, default value if not found
	 */
	public String getProperty(G_PROPERTIES name)
	{
		if (properties == null)
			loadConfiguration();
		return properties.getProperty(name.name(), defaultValues.get(name));
	}

	protected void loadConfiguration()
	{
		String configFileName = getConfigurationFileName();
		if (configFileName != null && new File(configFileName).canRead())
		try 
		{
			System.out.println("Loaded configuration file "+configFileName);
			XMLDecoder decoder = new XMLDecoder(new FileInputStream(configFileName));
			properties = (Properties) decoder.readObject();
			windowCoords = (HashMap<String, WindowPosition>) decoder.readObject();
			decoder.close();
		} catch (Exception e) 
		{// failed loading, (almost) ignore this.
			System.err.println("Failed to load "+configFileName);
			e.printStackTrace();
		}
		
		if (windowCoords == null)
			windowCoords = new HashMap<String, WindowPosition>();
		if (properties == null)
			properties = new Properties();
	}

	/** Retrieves the name of the file to load configuration information from/store it to.
	 * 
	 * @return the file name to load/store configuration information information, or return null if it cannot be retrieved. 
	 */
	protected static String getConfigurationFileName()
	{
		String path = System.getProperty(ENV_PROPERTIES.VIZ_DIR.name());if (path == null) path="resources"+File.separator+"graphLayout";
		String file = System.getProperty(ENV_PROPERTIES.VIZ_CONFIG.name());
		String result = null;
		if (file != null)
			result = path+File.separator+file+".xml";
		
		return result;
	}

	/** Loads the location/size of a frame from the properties file and positions the frame as appropriate.
	 * 
	 * @param frame the frame to position.
	 * @param name the name of the property to load from
	 */   
	public WindowPosition loadFrame(G_PROPERTIES name)
	{
		if (windowCoords == null)
			loadConfiguration();
		
		WindowPosition result = windowCoords.get(name.name());
		
		if (result == null)
		{// invent default coordinates, using http://java.sun.com/j2se/1.5.0/docs/api/java/awt/GraphicsDevice.html#getDefaultConfiguration()

			GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
			GraphicsDevice[] gs = ge.getScreenDevices();
			int deviceToUse = Integer.valueOf(getProperty(G_PROPERTIES.GRAPHICS_MONITOR));
			if (deviceToUse >= gs.length) deviceToUse =statechum.GlobalConfiguration.DEFAULT_SCREEN;// use the first one if cannot use the requested one.
			GraphicsConfiguration gc = gs[deviceToUse].getDefaultConfiguration();
			
			// from http://java.sun.com/j2se/1.4.2/docs/api/index.html
			Rectangle shape = gc.getBounds();
			Rectangle rect = new Rectangle(new Rectangle(shape.x, shape.y,400,300));
			if (name == G_PROPERTIES.LOWER)
				rect.y+=rect.getHeight()+30;
			result = new WindowPosition(rect,deviceToUse);
			windowCoords.put(name.name(),result);
		}
		
		return result;
	}
	
	/** Stores the current location/size of a frame to the properties file.
	 * 
	 * @param frame the frame to position.
	 * @param name the name under which to store the property
	 */   
	public void saveFrame(Frame frame,G_PROPERTIES name)
	{
		WindowPosition windowPos = windowCoords.get(name.name());if (windowPos == null) windowPos = new WindowPosition();
		Rectangle newRect = new Rectangle(frame.getSize());newRect.setLocation(frame.getX(), frame.getY());
		windowPos.setRect(newRect);
		windowCoords.put(name.name(), windowPos);
	}

	/** Stores the details of the frame position. 
	 */
	public static class WindowPosition
	{
		private Rectangle rect = null;
		private int screenDevice;
		
		public WindowPosition() {}

		public WindowPosition(Rectangle r, int s)
		{
			rect = r;screenDevice = s;
		}
		
		public Rectangle getRect() {
			return rect;
		}

		public void setRect(Rectangle r) {
			this.rect = r;
		}

		public int getScreenDevice() {
			return screenDevice;
		}

		public void setScreenDevice(int screen) {
			this.screenDevice = screen;
		}

		
	}
	
	/** Saves all the current properties in the configuration file. */
	public void saveConfiguration()
	{
		String configFileName = getConfigurationFileName();
		if (windowCoords == null)
			windowCoords = new HashMap<String, WindowPosition>();
		if (properties == null)
			properties = new Properties();

		try {
			if (configFileName != null)
			{
				XMLEncoder encoder = new XMLEncoder(new FileOutputStream(configFileName));
				encoder.writeObject(properties);encoder.writeObject(windowCoords);encoder.close();
			}
		} catch (Exception e) 
		{// failed loading
			e.printStackTrace();
		}		
	}

	/** Returns true if the configuration file defines the name of the supplied graph as the one 
	 * transformation of which we should be looking at in detail.
	 * 
	 * @param graph the graph we might wish to look at in detail
	 * @return whether lots of debug output should be enable when this graph is being processed.
	 */
	public boolean isGraphTransformationDebug(DirectedSparseGraph graph)
	{
		String name = graph == null? null:(String)graph.getUserDatum(JUConstants.TITLE);
		return name != null && name.length()>0 && 
			getProperty(G_PROPERTIES.STOP).equals(name);
	}
}