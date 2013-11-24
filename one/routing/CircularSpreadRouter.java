package routing;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import core.Connection;
import core.Coord;
import core.DTNHost;
import core.Message;
import core.Settings;

public class CircularSpreadRouter extends ActiveRouter {
	
	/** identifier for the initial number of copies setting ({@value})*/ 
	public static final String NROF_COPIES = "nrofCopies";
	/** identifier for the initial number of copies setting ({@value})*/ 
	public static final String DIRECTION_COEFF = "directionCoefficient";
	/** identifier for the binary-mode setting ({@value})*/ 
	public static final String BINARY_MODE = "binaryMode";
	/** SprayAndWait router's settings name space ({@value})*/ 
	public static final String CIRCULARSPREAD_NS = "CircularSpreadRouter";
	/** Message property key */
	public static final String MSG_COUNT_PROPERTY = CIRCULARSPREAD_NS + "." +
		"copies";
	
	protected int initialNrofCopies;
	protected boolean isBinary;
	protected int directionCoefficient;
	
	public CircularSpreadRouter(Settings s) {
		super(s);
		Settings csnwSettings = new Settings(CIRCULARSPREAD_NS);
		initialNrofCopies = csnwSettings.getInt(NROF_COPIES);
//		directionCoefficient = csnwSettings.getInt(DIRECTION_COEFF);
		isBinary = csnwSettings.getBoolean(BINARY_MODE);
	}
	
	public CircularSpreadRouter(CircularSpreadRouter r) {
		super(r);
		this.initialNrofCopies = r.initialNrofCopies;
		this.isBinary = r.isBinary;
		this.directionCoefficient = r.directionCoefficient;
	}
	
	@Override
	public int receiveMessage(Message m, DTNHost from) {
		return super.receiveMessage(m, from);
	}
	
	@Override
	public Message messageTransferred(String id, DTNHost from) {
		Message msg = super.messageTransferred(id, from);
		Integer nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
		
		assert nrofCopies != null : "Not a CSnW message: " + msg;
		
		if (isBinary) {
			/* in binary S'n'W the receiving node gets ceil(n/2) copies */
			nrofCopies = (int)Math.ceil(nrofCopies/2.0);
		}
		else {
			/* in standard S'n'W the receiving node gets only single copy */
			nrofCopies = 1;
		}
		
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
		return msg;
	}
	
	@Override 
	public boolean createNewMessage(Message msg) {
		makeRoomForNewMessage(msg.getSize());

		msg.setTtl(this.msgTtl);
		msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
		addToMessages(msg, true);
		return true;
	}
	
	
	@Override
	public void update() {
		super.update();
		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}
		
		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}
		
		/* create a list of SAWMessages that have copies left to distribute */
		@SuppressWarnings(value = "unchecked")
		List<Message> copiesLeft = sortByQueueMode(getMessagesWithCopiesLeft());
		
		if (copiesLeft.size() > 0) {
			/* try to send those messages */
			List<Connection> connections = getConnections();
			trySpreadingMessagesInConnections(connections, copiesLeft);
		}
	}
	
	
	protected Connection trySpreadingMessagesInConnections(List<Connection> connections, List<Message> copiesLeft){
		
		if (connections.size() == 0 || this.getNrofMessages() == 0) {
			return null;
		}
		List<Connection> spreadingConnections = filterConnections(connections);
		return tryMessagesToConnections(copiesLeft, spreadingConnections);
		
	}
		
	
	private List<Connection> filterConnections(List<Connection> connections){
		//DTNHost host = getHost();
		double split = 5;
		double selfDir = getDirectionofHost(getHost());
		List<Connection> filteredConnections = new LinkedList<Connection>();
		Map<Double, Connection> directionConnectionMap = new TreeMap<Double, Connection>();
		
		for(Connection conn : connections){
			double peerDir = getDirectionofHost(conn.getOtherNode(getHost()));
			if (peerDir == -1 || selfDir == -1)
				continue;
			double directionDeviation = Double.valueOf(Math.abs(selfDir-peerDir));
			if (directionDeviation > (Math.PI / split)) {
				filteredConnections.add(conn);
			}
		}
		return filteredConnections;
		
	/*	int interval = connections.size()/1;
		double count = 0;
		for(Connection conn : directionConnectionMap.values()) {
			count++;
			if(count%interval == 0){
				filteredConnections.add(conn);
			}
		}
		if(filteredConnections.isEmpty() && !connections.isEmpty()){
			return connections;
		} else {
			// return a subset of collections
			return filteredConnections;
		}
	*/	
		
	}
	
	private double getDirectionofHost(DTNHost host) {
		if(null != host.getPath() && host.getPath().getCoords().size() == 2) {
			Coord selfLoc = host.getPath().getCoords().get(0);
			Coord nextLoc = host.getPath().getCoords().get(1);
			return getDirection(selfLoc, nextLoc);
		} else {
			//System.err.println("Direction of host cannot be found");
			return -1d;
		}
		
	}

	private double getDirection(Coord selfLoc, Coord nextLoc) {
		double rise = nextLoc.getY()-selfLoc.getY();
		double run = nextLoc.getX()-selfLoc.getX();
		double radian_direction = Math.atan2(rise, run);
		
		if (rise < 0) {
			return (2 * Math.PI + radian_direction);   // For any downward direction, the radian_direction is < 0,
		}											   // hence we add it to 360 to get mean direction in radian
		return (radian_direction);
	}

	@Override
	public CircularSpreadRouter replicate() {
		return new CircularSpreadRouter(this);
	}
	
	/**
	 * Creates and returns a list of messages this router is currently
	 * carrying and still has copies left to distribute (nrof copies > 1).
	 * @return A list of messages that have copies left
	 */
	protected List<Message> getMessagesWithCopiesLeft() {
		List<Message> list = new ArrayList<Message>();

		for (Message m : getMessageCollection()) {
			Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROPERTY);
			assert nrofCopies != null : "SnW message " + m + " didn't have " + 
				"nrof copies property!";
			if (nrofCopies > 1) {
				list.add(m);
			}
		}
		
		return list;
	}
	/**
	 * Called just before a transfer is finalized (by 
	 * {@link ActiveRouter#update()}).
	 * Reduces the number of copies we have left for a message. 
	 * In binary Spray and Wait, sending host is left with floor(n/2) copies,
	 * but in standard mode, nrof copies left is reduced by one. 
	 */
	@Override
	protected void transferDone(Connection con) {
		Integer nrofCopies;
		String msgId = con.getMessage().getId();
		/* get this router's copy of the message */
		Message msg = getMessage(msgId);

		if (msg == null) { // message has been dropped from the buffer after..
			return; // ..start of transfer -> no need to reduce amount of copies
		}
		
		/* reduce the amount of copies left */
		nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
		if (isBinary) { 
			nrofCopies /= 2;
		}
		else {
			nrofCopies--;
		}
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
	}
	
}
