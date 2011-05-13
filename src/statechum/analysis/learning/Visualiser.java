/* Copyright (c) 2006, 2007, 2008 Neil Walkinshaw and Kirill Bogdanov
 * 
 * This file is part of StateChum
 * 
 * StateChum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * StateChum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with StateChum.  If not, see <http://www.gnu.org/licenses/>.
 */
package statechum.analysis.learning;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

import edu.uci.ics.jung.visualization.*;
import edu.uci.ics.jung.visualization.contrib.*;
import edu.uci.ics.jung.visualization.control.DefaultModalGraphMouse;
import edu.uci.ics.jung.visualization.control.ModalGraphMouse;
import edu.uci.ics.jung.visualization.control.PickingGraphMousePlugin;
import edu.uci.ics.jung.visualization.control.ScalingGraphMousePlugin;
import edu.uci.ics.jung.graph.*;
import edu.uci.ics.jung.graph.decorators.*;
import edu.uci.ics.jung.graph.impl.DirectedSparseGraph;
import statechum.DeterministicDirectedSparseGraph;
import statechum.GlobalConfiguration;
import statechum.GlobalConfiguration.WindowPosition;
import statechum.JUConstants;
import statechum.DeterministicDirectedSparseGraph.DeterministicEdge;
import statechum.GlobalConfiguration.G_PROPERTIES;
import statechum.Label;
import statechum.analysis.Erlang.ErlangLabel;
import statechum.analysis.learning.rpnicore.*;

import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;

import javax.swing.*;

import org.apache.commons.collections.Predicate;

/* Graph layout loading/saving including most of XMLPersistingLayout is from Jung source code. 
 * 
 * Sample JVM arguments (if configuration file does not exist it will be created when configuration is saved):
 * -ea -DVIZ_CONFIG=kirill_home -Dthreadnum=2 -Djava.library.path=linear/.libs -Xmx3500m
 * For troubleshooting slow-running tests, the following can be used:
 * -ea -DVIZ_CONFIG=kirill_home -Dthreadnum=2 -Djava.library.path=linear/.libs:smt/.libs -Xmx3500m -Dcom.sun.management.jmxremote
 * Sample JVM arguments for a Mac:
 * -ea -DVIZ_CONFIG=kirill_home -Dthreadnum=2 -Djava.library.path=linear/.libs:smt/.libs -Xmx3500m -Djava.awt.headless=true -DLTL2BA=/usr/local/soft/ltl2ba-1.1/bin/ltl2ba
 * 
 * JVM arguments for auto-answering queries:
 * -ea -DVIZ_CONFIG=kirill_office -Dthreadnum=2 -Djava.library.path=linear/.libs;smt/.libs -Xmx1500m -DVIZ_AUTOFILENAME=resources/aseQuestionsAndAnswers
 *
 * JVM arguments for DiffExperiments:
 * -ea -DVIZ_CONFIG=kirill_office -DVIZ_DIR=exp1/resources/graphLayout -Dthreadnum=2 -Djava.library.path=exp1/linear/.libs;exp1/smt/.libs;"D:/R-2.12.2/library/rJava/jri"  -Xmx1500m
 * as above on Debian x86_64:
 * -ea -DVIZ_CONFIG=kirill_home -DVIZ_DIR=exp1/resources/graphLayout -Dthreadnum=2 -Djava.library.path=exp1/linear/.libs:exp1/smt/.libs:/usr/local/lib/R/site-library/rJava/jri -Xmx3500m
 * R_HOME=/usr/lib/R
 * as above on MacOS X 10.5.8,
 * -ea -DVIZ_CONFIG=kirill_home -DVIZ_DIR=exp1/resources/graphLayout -Dthreadnum=2 -Djava.library.path=exp1/linear/.libs:exp1/smt/.libs:/Library/Frameworks/R.framework/Versions/2.12/Resources/library/rJava/jri -Xmx3500m
 * R_HOME=/Library/Frameworks/R.framework/Versions/2.12/Resources
 * 
 * Updating Statechum web page:
 * 
(cd /home/groups/s/st/statechum; svn export --force http://statechum.svn.sourceforge.net/svnroot/statechum/XMachineTool/trunk/htdocs )
 * 
Jung license included below,

The license below is the BSD open-source license. See 
http://www.opensource.org/licenses/bsd-license.php
with:
<OWNER> = Regents of the University of California and the JUNG Project
<ORGANIZATION> = University of California
<YEAR> = 2003 

It allows redistribution of JUNG freely, albeit with acknowledgement of JUNG's being a component in the redistributed software. However, we would greatly appreciate if you can let us know what you are doing with JUNG.

--
THE JUNG LICENSE

Copyright (c) 2003-2004,  Regents of the University of California and the JUNG Project 
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * Neither the name of the University of California nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
public class Visualiser extends JFrame implements Observer, Runnable, MouseListener 
{
    /**
     * The version ID for serialisation.
     */
    private static final long serialVersionUID = -6382530787840924374L;
    protected VisualizationViewer viewer = null;
    
    /** We'd like to store a number of graphs and switch between them, but
     * knowing the name (i.e. the layout) is not enough - we need to store
     * graphs themselves, which is accomplished using this map.
     */
    protected List<DirectedSparseGraph> graphs = new LinkedList<DirectedSparseGraph>();
    
    protected static class LayoutOptions
    {
    	public boolean showNegatives = true;
    }
    
    protected List<LayoutOptions> layoutOptions = new LinkedList<LayoutOptions>();
    
    /** Current position in the above list. */
    protected int currentGraph;
    /**
     * The identifier under which to store window information. The default value is
     * negative which inhibits saving of a layout.
     */
    protected int propName = -1;
    
    /** Global configuration. */
    static final GlobalConfiguration globalConfig = GlobalConfiguration.getConfiguration();

    /** A popup with Jung control choices. */
    JPopupMenu popupMenu;

    /**
     * Public constructor
     *
     * @param windowPropName
     *            the name under which to store configuration information.
     */
    public Visualiser(int windowPropName) {
      super(GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()[GlobalConfiguration.getConfiguration().loadFrame(windowPropName).getScreenDevice()].getDefaultConfiguration());
      propName = windowPropName;
    }

    /**
     * Public constructor, constructs a visualiser which cannot save or load a
     * layout.
     */
    public Visualiser() {
        propName = -1;
    }
    
    /** Key bindings. */
    Map<Integer, Action> keyToActionMap = new TreeMap<Integer, Action>();
    /** Actions to switch to picking/transform mode. */
    Action pickAction, transformAction, persistAction;

    class WindowEventHandler extends WindowAdapter {

        @Override
        public void windowClosing(@SuppressWarnings("unused") WindowEvent evt) {
            
        }
    }

    /** A kind action used by this interface. */
    public static abstract class graphAction extends AbstractAction {

        /**
         * ID for serialization.
         */
        private static final long serialVersionUID = -939337272930400183L;

        public graphAction() {
            // empty default constructor.
        }

        public graphAction(String name, String description) {
            super(name);
            putValue(SHORT_DESCRIPTION, description);
        }
    }

    protected void setVisualiserKeyBindings() {
        persistAction = new graphAction("saveLayout", "save the layout of the visible graph") {

            /** Serial number. */
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(@SuppressWarnings("unused") ActionEvent e) {
                try {
                    if (propName >= 0) {
                        String fileName = getLayoutFileName(graphs.get(currentGraph));
                        XMLEncoder encoder = new XMLEncoder(new FileOutputStream(fileName));
                        Map<Integer, DoublePair> layout = ((XMLPersistingLayout) viewer.getModel().getGraphLayout()).persist();
                        encoder.writeObject(layout);
                        XMLAffineTransformSerialised trV = new XMLAffineTransformSerialised();
                        trV.setFromAffineTransform(viewer.getViewTransformer().getTransform());
                        encoder.writeObject(trV);
                        XMLAffineTransformSerialised trL = new XMLAffineTransformSerialised();
                        trL.setFromAffineTransform(viewer.getLayoutTransformer().getTransform());
                        encoder.writeObject(trL);
                        ((XMLModalGraphMouse) viewer.getGraphMouse()).store(encoder);
                        encoder.writeObject(layoutOptions);
                        encoder.close();
                    }
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
        };
        keyToActionMap.put(KeyEvent.VK_F2, persistAction);
        keyToActionMap.put(KeyEvent.VK_F3, new graphAction("loadLayout", "loads the previously saved layout the visible graph") {

            /** Serial number. */
            private static final long serialVersionUID = 2L;

            @Override
            public void actionPerformed(@SuppressWarnings("unused") ActionEvent e) {
                reloadLayout(false);
            }
        });
        keyToActionMap.put(KeyEvent.VK_F9, new graphAction("loadPreviousLayout", "loads the layout of the previous graph in the list") {

            /** Serial number. */
            private static final long serialVersionUID = 3L;

            @Override
            public void actionPerformed(@SuppressWarnings("unused") ActionEvent e) {
                if (currentGraph > 0) {
                    restoreLayout(false, currentGraph - 1);
                }
            }
        });

        pickAction = new graphAction("pick", "Switches Jung into picking mode") {

            /** Serial number. */
            private static final long serialVersionUID = 7L;

            @Override
            public void actionPerformed(@SuppressWarnings("unused") ActionEvent e) {
                ((XMLModalGraphMouse) viewer.getGraphMouse()).setMode(ModalGraphMouse.Mode.PICKING);
            }
        };
        keyToActionMap.put(KeyEvent.VK_F11, pickAction);

        transformAction = new graphAction("transform", "Switches Jung into transformation mode") {

            /** Serial number. */
            private static final long serialVersionUID = 8L;

            @Override
            public void actionPerformed(@SuppressWarnings("unused") ActionEvent e) {
                ((XMLModalGraphMouse) viewer.getGraphMouse()).setMode(ModalGraphMouse.Mode.TRANSFORMING);
            }
        };
        keyToActionMap.put(KeyEvent.VK_F12, transformAction);

        keyToActionMap.put(KeyEvent.VK_UP, new graphAction("previous", "loads the previous graph") {

            /** Serial number. */
            private static final long serialVersionUID = 9L;

            @Override
            public void actionPerformed(@SuppressWarnings("unused") ActionEvent e) {
                if (currentGraph > 0) {
                    --currentGraph;
                    reloadLayout(false);
                }
            }
        });
        keyToActionMap.put(KeyEvent.VK_DOWN, new graphAction("next", "loads the next graph") {

            /** Serial number. */
            private static final long serialVersionUID = 10L;

            @Override
            public void actionPerformed(@SuppressWarnings("unused") ActionEvent e) {
                if (currentGraph < graphs.size() - 1) {
                    ++currentGraph;
                    reloadLayout(false);
                }
            }
        });
        
        keyToActionMap.put(KeyEvent.VK_F, new graphAction("negatives", "toggles negatives on or off") {

            /** Serial number. */
            private static final long serialVersionUID = 11L;

            @Override
            public void actionPerformed(@SuppressWarnings("unused") ActionEvent e) {
            	LayoutOptions options = layoutOptions.get(currentGraph);
                if (options != null) {
                	options.showNegatives = !options.showNegatives; 
                    reloadLayout(false);
                }
            }
        });
    }

    /** Creates key bindings used in all Statechum windows.
     * 
     * @param frame frame of the window
     * @param windowID the identifier of the window in the config file - used to store/restore window positions
     * @param keyToActionMap map to store key bindings in.
     */
    public static void setStateChumKeyBindings(final JFrame frame,final int windowID,Map<Integer, Action> keyToActionMap)
    {
        keyToActionMap.put(KeyEvent.VK_F4, new graphAction("saveWindows", "save the current position/size of graph windows") {

            /** Serial number. */
            private static final long serialVersionUID = 4L;

            @Override
            public void actionPerformed(@SuppressWarnings("unused") ActionEvent e) {
                globalConfig.saveFrame(frame, windowID);
                globalConfig.saveConfiguration();
            }
        });
        
        keyToActionMap.put(KeyEvent.VK_ESCAPE, new graphAction("terminate", "terminates this program") {

            /** Serial number. */
            private static final long serialVersionUID = 5L;

            @Override
            public void actionPerformed(@SuppressWarnings("unused") ActionEvent e) {
                frame.setVisible(false);
                frame.dispose();
                Visualiser.syncValue.set(true);
                DrawGraphs.end();
                System.exit(1);
                /*
                synchronized (Visualiser.syncObject) {
                Visualiser.syncObject.notify();
                }*/
            }
        });
        keyToActionMap.put(KeyEvent.VK_SPACE, new graphAction("step", "exits the Visualiser.waitForKey() call") {

            /** Serial number. */
            private static final long serialVersionUID = 6L;

            @Override
            public void actionPerformed(@SuppressWarnings("unused") ActionEvent e) {
                Visualiser.syncValue.set(false);
                synchronized (Visualiser.syncObject) {
                    Visualiser.syncObject.notify();
                }
            }
        });

    }
    
    public void construct(Graph g,LayoutOptions options) {
        if (!globalConfig.isAssertEnabled() && Boolean.getBoolean(globalConfig.getProperty(G_PROPERTIES.ASSERT))) {
            System.err.println("Pass the -ea argument to JVM to enable assertions");
        }

        //this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        this.addWindowListener(new WindowEventHandler());
        this.addComponentListener(new ComponentListener() {
			
			@Override
			public void componentShown(@SuppressWarnings("unused") ComponentEvent e) {}
			
			@Override
			public void componentResized(@SuppressWarnings("unused") ComponentEvent e) {
				if (viewer != null) viewer.getModel().getGraphLayout().resize(getSize());
			}
			
			@Override
			public void componentMoved(@SuppressWarnings("unused") ComponentEvent e) {}
			
			@Override
			public void componentHidden(@SuppressWarnings("unused") ComponentEvent e) {}
		});
        this.addKeyListener(new KeyListener() {

            @Override
            public void keyPressed(KeyEvent arg0) {
                Action act = keyToActionMap.get(arg0.getKeyCode());
                if (act != null) {
                    act.actionPerformed(null);
                }
            }

            @Override
            public void keyReleased(@SuppressWarnings("unused") KeyEvent arg0) {// this method is intentionally left blank - keypresses/releases are handled by the keyPressed method.
            }

            @Override
            public void keyTyped(@SuppressWarnings("unused") KeyEvent key) {// this method is intentionally left blank - keypresses/releases are handled by the keyPressed method.
            }
        });

        popupMenu = new JPopupMenu();
       // Icon loading is from http://www.javaworld.com/javaworld/javaqa/2000-06/03-qa-0616-icon.html
        Image icon = Toolkit.getDefaultToolkit().getImage("resources" + File.separator + "icon.jpg");
        if (icon != null) {
            setIconImage(icon);
        }

        setVisualiserKeyBindings();setStateChumKeyBindings(this, propName, keyToActionMap);
        updatePopupMenu(popupMenu,keyToActionMap);
        //getContentPane().removeAll();
        WindowPosition framePosition = globalConfig.loadFrame(propName);

        viewer = new VisualizationViewer(new DefaultVisualizationModel(new XMLPersistingLayout(
                propName >= 0 ? new FRLayout(g) : new KKLayout(g))), constructRenderer(g,options));

        viewer.setBackground(Color.WHITE);
        final DefaultModalGraphMouse graphMouse = new XMLModalGraphMouse();
        graphMouse.setMode(ModalGraphMouse.Mode.PICKING);
        graphMouse.add(new PickingGraphMousePlugin());
        viewer.setGraphMouse(graphMouse);
        viewer.setPickSupport(new ShapePickSupport());
        viewer.addMouseListener(this);viewer.setPreferredSize(getSize());
        final GraphZoomScrollPane panel = new GraphZoomScrollPane(viewer);
        getContentPane().add(panel);
        pack();
      
        restoreLayout(true, currentGraph);
        setBounds(framePosition.getRect());
        setVisible(true);
    }
    
    public static void updatePopupMenu(JPopupMenu popupMenu,Map<Integer, Action> keyToActionMap)
    {
        for(Entry<Integer,Action> ia:keyToActionMap.entrySet())
        {
        	ia.getValue().putValue(Action.ACCELERATOR_KEY,KeyStroke.getKeyStroke(ia.getKey(),0));
        	JMenuItem menuitem = new JMenuItem(ia.getValue());
        	popupMenu.add(menuitem);
        }

    }
    
    protected static class XMLModalGraphMouse extends DefaultModalGraphMouse {

        /** Restores parameters which are not recorded in the view/layout transforms, from the supplied XML stream
         *
         * @param decode XML decoder to use
         */
        public void restore(XMLDecoder decoder) {
            ((ScalingGraphMousePlugin) scalingPlugin).setIn((Float) decoder.readObject());
            ((ScalingGraphMousePlugin) scalingPlugin).setOut((Float) decoder.readObject());
        }

        /** Stores parameters which are not recorded in the view/layout transforms, to the supplied XML stream
         *
         * @param encoder XML encoder to use
         */
        public void store(XMLEncoder encoder) {
            encoder.writeObject(((ScalingGraphMousePlugin) scalingPlugin).getIn());
            encoder.writeObject(((ScalingGraphMousePlugin) scalingPlugin).getOut());
        }
    }

    /** Loads or reloads a graph. Used during initialisation on the Swing thread,
     * when graph changes or user hits F3 (reload).
     *
     * @param ignoreErrors Whether to ignore loading errors - they are ignored
     * on auto-load, but honoured on user load.
     */
    protected void reloadLayout(boolean ignoreErrors) {
        /** The graph currently being displayed. */
        final Graph graph = graphs.get(currentGraph);

        assert graph != null;
        String title = (String) graph.getUserDatum(JUConstants.TITLE) + " (" + (currentGraph + 1) + "/" + graphs.size() + ")";
        if (!wasInitialised) {
            construct(graph,layoutOptions.get(currentGraph));
            setTitle(title);
            wasInitialised = true;
        } else {
            viewer.getModel().setGraphLayout(new XMLPersistingLayout(propName >= 0 ? new FRLayout(graph) : new KKLayout(graph)));
            setTitle(title);
            restoreLayout(ignoreErrors, currentGraph);
            viewer.setRenderer(constructRenderer(graph,layoutOptions.get(currentGraph)));
        }

        viewer.getModel().getGraphLayout().resize(getSize());
    }

    
    /** Loads the layout of the specific graph in the list.
     *
     *  @param whether to ignore loading errors.
     *  @param graphNumber the number of the graph to load.
     */
    protected void restoreLayout(boolean ignoreErrors, int graphNumber) {
        try {
            String fileName = getLayoutFileName(graphs.get(graphNumber));
            if (propName >= 0 && (new File(fileName)).canRead()) {
                XMLDecoder decoder = new XMLDecoder(new FileInputStream(fileName));
                Map<Integer, DoublePair> map = (Map<Integer, DoublePair>) decoder.readObject();
                ((XMLPersistingLayout) viewer.getModel().getGraphLayout()).restore(map);

                // Most rotate/share/translate are stateless, so I only need to get the cumulative transform
                // for layout and view via getTransform() which should return AffineTransform
                // which I should be able to persist into XML.
                // Only ScalingGraphMousePlugin has a state
                // in the form of getIn()/setIn()/getOut()/setOut().
                viewer.getViewTransformer().setToIdentity();
                viewer.getViewTransformer().concatenate(
                        ((XMLAffineTransformSerialised) decoder.readObject()).getAffineTransform());
                viewer.getLayoutTransformer().setToIdentity();
                viewer.getLayoutTransformer().concatenate(
                        ((XMLAffineTransformSerialised) decoder.readObject()).getAffineTransform());
                ((XMLModalGraphMouse) viewer.getGraphMouse()).restore(decoder);
                layoutOptions = (List<LayoutOptions>)decoder.readObject();
                decoder.close();

                viewer.invalidate();
            }
        } catch (Exception e1) {
            if (!ignoreErrors) {
                e1.printStackTrace();
            }
        }
    }

    /** Used to serialise graph layout information into XML where XMLEncoder requires a top-level class.
     *
     * @author kirr
     *
     */
    public static class DoublePair {

        private double x, y;

        public DoublePair() {
            x = 0;
            y = 0;
        }

        public DoublePair(double a, double b) {
            setX(a);
            setY(b);
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public void setX(double a) {
            x = a;
        }

        public void setY(double b) {
            y = b;
        }
    }

    /** A serialised form of XMLAffineTransform
     *
     * @author Kirill
     *
     */
    public static class XMLAffineTransformSerialised {

        protected double[] matrix;

        public double[] getM() {
            return matrix;
        }

        public void setM(double[] m) {
            matrix = m;
        }

        AffineTransform getAffineTransform() {
            return new AffineTransform(matrix);
        }

        void setFromAffineTransform(AffineTransform tr) {
            matrix = new double[6];
            tr.getMatrix(matrix);
        }
    }

    protected static PluggableRenderer constructRenderer(Graph g,final LayoutOptions options) {
        PluggableRenderer r = new PluggableRenderer();
        r = labelEdges(g, r);
        r = labelVertices(r, g);
        if (options != null)
	        r.setVertexIncludePredicate(new Predicate(){
	        	@Override
				public boolean evaluate(Object object)
	        	{
	        		if (options.showNegatives)
	        			return true;
	        		else
	        			return DeterministicDirectedSparseGraph.isAccept((Vertex) object);
	        	}
	        });
        return r;
    }
    /** If the frame was not constructed, we have to construct instances of
     * all classes responsible for it; once it is build and only our
     * graph changed, it is enough to replace the layout to update the graph. */
    protected boolean wasInitialised = false;

    @Override
    public void run() {
        reloadLayout(true);
    }

    @Override
    public void update(@SuppressWarnings("unused") final Observable s, Object arg) {
        if (arg instanceof AbstractLearnerGraph) {
            graphs.add(((AbstractLearnerGraph) arg).pathroutines.getGraph());layoutOptions.add(new LayoutOptions());
        } else if (arg instanceof DirectedSparseGraph) {
        	DirectedSparseGraph gr = (DirectedSparseGraph) arg;
            graphs.add(gr);layoutOptions.add((LayoutOptions)gr.getUserDatum(JUConstants.LAYOUTOPTIONS));
        } else {
            System.err.println("Visualiser notified with unknown object " + arg);
        }
        
        currentGraph = graphs.size() - 1;
        SwingUtilities.invokeLater(this);
    }

    public static class EdgeColour {

        final Map<String, Map<String, Map<String, Color>>> transitionColours;
        final Map<String, String> extraLabels;

        public EdgeColour(Graph graph) {
            transitionColours = (Map<String, Map<String, Map<String, Color>>>) graph.getUserDatum(JUConstants.EDGE);
            extraLabels = (Map<String, String>) graph.getUserDatum(JUConstants.VERTEX);
        }

        /** Given an edge and a label, determines if there is an annotation associated with that label.
         *
         * @param e edge to consider
         * @param currentLabel label (an edge may have multiple labels if it represents multiple parallel edges)
         * @return colour if annotation is present and null otherwise.
         */
        public Color getEdgeColour(ArchetypeEdge e, Label currentLabel) {
            Color result = null;

            if (e instanceof DeterministicEdge) {
                DeterministicEdge edge = (DeterministicEdge) e;
                String source = edge.getSource().getUserDatum(JUConstants.LABEL).toString(), target = edge.getDest().getUserDatum(JUConstants.LABEL).toString();
                if (transitionColours != null && transitionColours.containsKey(source) && transitionColours.get(source).containsKey(currentLabel)) {
                    result = transitionColours.get(source).get(currentLabel).get(target);
                }
            }
            return result;
        }

        /** Given a vertex, extracts an annotation associated with that label.
         *
         * @param e edge to consider
         * @return annotation if present and null otherwise.
         */
        public String getPickedLabel(ArchetypeVertex v) {
            String result = null;
            Object lbl = v.getUserDatum(JUConstants.LABEL);
            if (lbl != null && extraLabels != null) {
                String label = lbl.toString();
                if (extraLabels.containsKey(label)) {
                    result = extraLabels.get(label);
                }
            }

            return result;
        }

        public boolean isPicked(ArchetypeVertex v) {
            return getPickedLabel(v) != null;
        }
        /** A colour of an inconsistent edge. */
        public final Color inconsistent = Color.MAGENTA;

        public Color getPickedColour(ArchetypeEdge e) {
            Set<Label> labels = (Set<Label>) e.getUserDatum(JUConstants.LABEL);
            Iterator<Label> labelIt = labels.iterator();
            Color col = null;
            while (labelIt.hasNext()) {
            	Label currentLabel = labelIt.next();
                Color newCol = getEdgeColour(e, currentLabel);
                if (col == null) {
                    col = newCol;
                } else if (col != newCol) {
                    col = inconsistent;
                }
            }
            return col;
        }
    }

    private static PluggableRenderer labelEdges(Graph graph, PluggableRenderer render) {
        final EdgeColour paintChooser = new EdgeColour(graph);
        EdgeStringer stringer = new EdgeStringer() {

            @Override
            public String getLabel(ArchetypeEdge e) {
                String result = "";

                if (e.containsUserDatumKey(JUConstants.LABEL)) 
                {
                    StringBuffer text = new StringBuffer();text.append("<html>");
                    boolean first=true;
                    for(Label lbl:(Set<Label>) e.getUserDatum(JUConstants.LABEL))
                    {
                    	if (!first) text.append("<br>");else first=false;
                    	if (!(lbl instanceof ErlangLabel))
                    	{
                    		text.append(lbl.toString());
                    	}
                    	else
                    	{
                    		ErlangLabel l = (ErlangLabel)lbl;
                    		text.append("<font color=blue>");text.append(l.callName);text.append("</font>,&nbsp;");
                    		text.append("<font color=black>");text.append(l.input);text.append("</font>");
                    		if (l.expectedOutput != null)
                    		{
                    			text.append(",&nbsp;<font color=green>");text.append(l.expectedOutput);text.append("</font>");
                    		}
                    	}
                    }
                    text.append("</html>");
                    result=text.toString();
                }

                return result;
            }
        };
        render.setEdgeStringer(stringer);
        render.setEdgePaintFunction(new AbstractEdgePaintFunction() {

            @Override
            public Paint getDrawPaint(Edge e) {
                Color result = paintChooser.getPickedColour(e);
                return result != null ? result : Color.BLACK;
            }
        });
        return render;
    }

    static class VertexShape extends AbstractVertexShapeFunction {

        public VertexShape() {
            super(new ConstantVertexSizeFunction(25),
                    new ConstantVertexAspectRatioFunction(1.0f));
        }

        @Override
        public Shape getShape(Vertex v) {
            JUConstants c = (JUConstants) v.getUserDatum(JUConstants.COLOUR);

            if (DeterministicDirectedSparseGraph.isInitial(v)) {
                return factory.getRegularStar(v, 7);
            } else if (!DeterministicDirectedSparseGraph.isAccept(v)) {
                return factory.getRectangle(v);
            } else if (c == JUConstants.INF_AMBER) {
                return factory.getRoundRectangle(v);
            }
            return factory.getEllipse(v);
        }
    }

    static class VertexPaint implements VertexPaintFunction {

        protected final PickedInfo picked;

        public VertexPaint(PickedInfo p) {
            picked = p;
        }

        @Override
        public Paint getDrawPaint(Vertex v) {
            if (v.getUserDatum(JUConstants.HIGHLIGHT) != null) {
                return Color.MAGENTA;
            }

            return Color.BLACK;
        }

        @Override
        public Paint getFillPaint(Vertex v) {
            Color col = Color.BLACK;

            if (picked.isPicked(v)) {
                col = Color.LIGHT_GRAY;
            } else {
                JUConstants c = (JUConstants) v.getUserDatum(JUConstants.COLOUR);
                if (c == null) {
                    col = Color.GREEN;
                } else {
                    if (c == JUConstants.RED) {
                        col = Color.PINK;
                    } else if (c == JUConstants.BLUE) {
                        col = Color.BLUE;
                    } else if (c == JUConstants.AMBER) {
                        col = Color.YELLOW;
                    } else if (c == JUConstants.INF_AMBER) {
                        col = new Color(210, 210, 0);
                    }
                    if (c == JUConstants.GRAY) {
                        col = Color.GRAY;
                    }
                }
            }
            return col;
        }
    }

    private static PluggableRenderer labelVertices(PluggableRenderer r, Graph graph) {
        StringLabeller labeller = StringLabeller.getLabeller(graph, "name");
        final EdgeColour paintChooser = new EdgeColour(graph);
        labeller.clear();
        Iterator<Vertex> labelIt = graph.getVertices().iterator();
        while (labelIt.hasNext()) {
            Vertex v = labelIt.next();
            try {
                String label = null;
                Object labelObj = v.getUserDatum(JUConstants.LABEL);
                if (labelObj != null) {
                    label = labelObj.toString();
                }
                if (label != null) {
                    String extraLabel = paintChooser.getPickedLabel(v);
                    String newLabel = label.toString() + (extraLabel != null ? " " + extraLabel : "");
                    labeller.setLabel(v, newLabel);
                }
            } catch (Exception e) {
                System.out.println(e);
                e.printStackTrace();
            }
        }
        
        r.setVertexStringer(labeller);
        r.setVertexShapeFunction(new VertexShape());
        r.setVertexPaintFunction(new VertexPaint(r));
        r.setVertexStrokeFunction(new ConstantVertexStrokeFunction(2.0f));
        return r;
    }
    /** Holds the JFrame to see the graphs being dealt with. Usage:
     * <pre>
     * 		updateFrame(upper,lower);// a public method
     * </pre>
     * where <i>upper</i> and <i>lower</i> are the graphs to be displayed.
     */
    static Visualiser graphWindow[] = new Visualiser[3];

    /** If a dialog box has to be displayed, it needs to know a reference frame. This method returns this frame, creating it if necessary.
     *
     * @return a Visualiser frame.
     */
    public static Visualiser getVisualiser() {
    	final int mainWindow = 0;
        if (graphWindow[mainWindow] == null) {
        	graphWindow[mainWindow] = new Visualiser(mainWindow);
        }
        return graphWindow[mainWindow];
    }

    /** Removes the two windows displaying Jung graphs.
     */
    public static void disposeFrame() {
	    try {
	        SwingUtilities.invokeAndWait(new Runnable() {
	
	            @Override
	            public void run() {
	            	for(int i=0;i<graphWindow.length;++i)
	            		if (graphWindow[i] != null)
	                	{
	                    	graphWindow[i].setVisible(false);
	                    	graphWindow[i].dispose();
	                    	graphWindow[i] = null;
	                	}
	            }
	        });
	    } catch (InterruptedException e) {
	        // cannot do much about this
	        e.printStackTrace();
	    } catch (InvocationTargetException e) {
	        // cannot do much about this
	        e.printStackTrace();
	    }
    }

    /** Displays twos graphs passed as arguments in the Jung window.
     * This method is to be called for one (g != null) or two (g != null && lowerGraph != null) windows to be displayed;
     * when both arguments are null, graphs are disposed of.
     *
     * @param upperGraph the graph to display
     * @param lowerGraph the graph to display below it
     */
    public static void updateFrame(final Object upperGraph, final Object lowerGraph) {
        if (upperGraph == null && lowerGraph == null) {// destruction of windows
            disposeFrame();
        } else {// construction of windows
            if (upperGraph == null) {
                throw new IllegalArgumentException("the first graph to display cannot be null");
            }

            updateFrameWithPos(upperGraph, 0);
            if (lowerGraph != null)
            	updateFrameWithPos(lowerGraph, 1);
        }
    }
    
    public static void updateFrameWithPos(final Object graph, int windowid)
    {
        if (graphWindow[windowid] == null) {
        	graphWindow[windowid] = new Visualiser(windowid);
        }
        graphWindow[windowid].update(null,graph);
    }
    
    /** Used to make it possible to single-step through graph transforms. */
    final static Object syncObject = new Object();
    /** Value to return to a thread which is waiting. */
    final static AtomicBoolean syncValue = new AtomicBoolean();

    /** Waits for a user to hit space on any of the visualiser windows.
     *
     * @return true if user hit space, false if application is terminating.
     */
    public static void waitForKey() {
        synchronized (syncObject) {
            try {
                syncObject.wait();
            } catch (InterruptedException e) {
                // assume we are terminating.
            }
        }

        //if (syncValue.get())
        //	System.exit(1);
    }

    /** Retrieves the name of the file to load a graph layout from/store layout to.
     *
     * @param g the graph which name to extract
     * @return the file name to load/store layout information, or throw IllegalArgumentException if it cannot be retrieved.
     */
    protected static String getLayoutFileName(Graph g) {
        String file = (String) g.getUserDatum(JUConstants.TITLE);
        String path = System.getProperty(G_PROPERTIES.VIZ_DIR.name());
        if (path == null) {
            path = "resources" + File.separator + "graphLayout";
        }
        if (file == null) {
            throw new IllegalArgumentException("cannot obtain graph name, the " + JUConstants.TITLE.name() + " property has not been set on the graph");
        }

        return path + File.separator + file.replace(' ', '_') + ".xml";
    }

    protected static class XMLPersistingLayout extends PersistentLayoutImpl {

        public XMLPersistingLayout(Layout layout) {
            super(layout);
        }

        
        
        /** Almost verbatim from Jung source code.
         *
         * Saves all the vertex locations to a map - this is enough to rebuild the layout
         * later since we'll know the connections later anyway.
         *
         * @return map containing the layout.
         */
        public Map<Integer, DoublePair> persist() {
            if (sourceMap == null) {
                sourceMap = new TreeMap<Integer, DoublePair>();
            }
            Set<Vertex> set = getGraph().getVertices();
            for (Iterator<Vertex> iterator = set.iterator(); iterator.hasNext();) {
                Vertex v = iterator.next();
                DoublePair p = new DoublePair(getX(v), getY(v));
                sourceMap.put(new Integer(v.hashCode()), p);
            }
            //encoder.writeObject(sourceMap);
            return sourceMap;
        }
        /** Stores the layout loaded from a file. The idea is to merge it with new one before
         * storing it back. This permits storing positions of vertices not in the layout,
         * thus permitting the same layout file to be used for different graphs. */
        private Map<Integer, DoublePair> sourceMap = null;

        /** Almost verbatim from Jung source code.
         * Restores all vertex locations from a file; does nothing if loading fails.
         *
         * @param map to load from
         */
        public void restore(Map<Integer, DoublePair> loadedMap) {
            if (sourceMap == null) {
                sourceMap = new TreeMap<Integer, DoublePair>();
            }
            sourceMap.putAll(loadedMap);
            for (Iterator<Map.Entry<Integer, DoublePair>> mi = sourceMap.entrySet().iterator(); mi.hasNext();) {
                Map.Entry<Integer, DoublePair> e = mi.next();
                DoublePair p = e.getValue();
                map.put(e.getKey(), new PersistentLayout.Point(p.getX(), p.getY()));
            }
            initializeLocations();
            locked = true;
        }
    }

    @Override
    public void mouseClicked(@SuppressWarnings("unused") MouseEvent e) {
        // this particular mouse operation is not handled - see mousePressed/mouseReleased
    }

    @Override
    public void mouseEntered(@SuppressWarnings("unused") MouseEvent e) {
        // this particular mouse operation is not handled - see mousePressed/mouseReleased
    }

    @Override
    public void mouseExited(@SuppressWarnings("unused") MouseEvent e) {
        // this particular mouse operation is not handled - see mousePressed/mouseReleased
    }

    // The following is from http://java.sun.com/docs/books/tutorial/uiswing/components/menu.html#popup
    @Override
    public void mousePressed(MouseEvent e) {
        maybeShowPopup(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        maybeShowPopup(e);
    }

    private void maybeShowPopup(MouseEvent e) {
        if (e.isPopupTrigger()) {
            popupMenu.show(e.getComponent(),
                    e.getX(), e.getY());
        }
    }
}
