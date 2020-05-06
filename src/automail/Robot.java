package automail;

import exceptions.BreakingFragileItemException;
import exceptions.ExcessiveDeliveryException;
import exceptions.ItemTooHeavyException;
import strategies.IMailPool;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static automail.Building.occupying_floot;

/**
 * The robot delivers mail!
 */
public class Robot {
	
    static public final int INDIVIDUAL_MAX_WEIGHT = 2000;

    IMailDelivery delivery;
    protected final String id;



    /** Possible states the robot can be in */
    public enum FloorState {UNFRAGILEITEM,FRAGILEITEM}
    public enum RobotState { DELIVERING, WAITING, RETURNING ,WRAPPING, FRAGILE_SLEEPING,NORMAL_SLEEPING,UNWRAPPING,DELIVERING_FRAGILE}
    public RobotState current_state;
    public int current_floor;//最后
    private int destination_floor;
    private int wrapping_time = 0;

    private IMailPool mailPool;
    private boolean receivedDispatch;
    
    private MailItem deliveryItem = null;
    private MailItem tempItem = null;
    private MailItem tube = null;
    private MailItem fragileItem = null;

    
    private int deliveryCounter;
    

    /**
     * Initiates the robot's location at the start to be at the mailroom
     * also set it to be waiting for mail.
     * @param //behaviour governs selection of mail items for delivery and behaviour on priority arrivals
     * @param delivery governs the final delivery
     * @param mailPool is the source of mail items
     */
    public Robot(IMailDelivery delivery, IMailPool mailPool){
    	id = "R" + hashCode();
        // current_state = RobotState.WAITING;
    	current_state = RobotState.RETURNING;
        current_floor = Building.MAILROOM_LOCATION;
        this.delivery = delivery;
        this.mailPool = mailPool;
        this.receivedDispatch = false;//未收到派遣
        this.deliveryCounter = 0;
    }
    
    public void dispatch() {
    	receivedDispatch = true;
    }

    /**
     * This is called on every time step 这在每个time step上都被调用
     * @throws ExcessiveDeliveryException if robot delivers more than the capacity of the tube without refilling
     */
    public void step() throws ExcessiveDeliveryException {    	
    	switch(current_state) {
    		/** This state is triggered when the robot is returning to the mailroom after a delivery 传送后机器人返回邮件室时触发此状态*/
    		case RETURNING:
    			/** If its current position is at the mailroom, then the robot should change state 如果其当前位置在邮件室，则机器人应更改状态*/
                if(current_floor == Building.MAILROOM_LOCATION){
                	if (tube != null) {//如果此时管道里有mail
                		mailPool.addToPool(tube);
                        System.out.printf("T: %3d >  +addToPool [%s]%n", Clock.Time(), tube.toString());
                        tube = null;
                	}
        			/** Tell the sorter the robot is ready 如果此时管道里没有mail，告诉分拣机机器人已经准备好*/
        			mailPool.registerWaiting(this);
                	changeState(RobotState.WAITING);
                } else {
                	/** If the robot is not at the mailroom floor yet, then move towards it! 如果机器人还没有在邮件室地板上，请朝它移动*/
                    moveTowards(Building.MAILROOM_LOCATION);
                	break;
                }
    		case WAITING:
                /** If the StorageTube is ready and the Robot is waiting in the mailroom then start the delivery
                 * 如果StorageTube准备就绪，并且Robot正在邮件室中等待，则开始传送*/
                if(!isEmpty() && receivedDispatch){//手臂和包中都为空 且已经被安排任务
                	receivedDispatch = false;
                	deliveryCounter = 0; // reset delivery counter
        			setRoute();

        			/** 2020.5.5 18:06：加判断机器人是否需要wrapping的判断*/
        			if (this.fragileItem!=null){
        			    //if (wrapping_time != 2){
                            changeState(RobotState.WRAPPING);
                        /*}
                        else {
                            changeState(RobotState.DELIVERING_FRAGILE);
                        }*/
                    }
        			else {
                        changeState(RobotState.DELIVERING);
                    }
                }
                break;
            case DELIVERING:
                if(current_floor == destination_floor){ // If already here drop off either way
                    /** Delivery complete, report this to the simulator! */
                    occupying_floot.put(current_floor,FloorState.UNFRAGILEITEM);
                    delivery.deliver(deliveryItem);
                    occupying_floot.remove(current_floor);
                    deliveryItem = null;
                    deliveryCounter++;
                    if(deliveryCounter > 3){  // Implies a simulation bug
                        throw new ExcessiveDeliveryException();
                    }
                    /** Check if want to return, i.e. if there is no item in the tube*/
                    if(tube == null){
                        changeState(RobotState.RETURNING);
                    }
                    else{
                        /** If there is another item, set the robot's route to the location to deliver the item */
                        deliveryItem = tube;
                        tube = null;
                        setRoute();
                        changeState(RobotState.DELIVERING);
                    }
                } else {
                    /** The robot is not at the destination yet, move towards it! */
                    if (occupying_floot.containsKey(current_floor + 1)) {
                        if (occupying_floot.get(current_floor + 1).equals(FloorState.FRAGILEITEM)) {
                            changeState(RobotState.NORMAL_SLEEPING);
                        } else {
                            moveTowards(destination_floor);
                        }
                    } else if (occupying_floot.containsKey(current_floor - 1)) {
                        if (occupying_floot.get(current_floor - 1).equals(FloorState.FRAGILEITEM)) {
                            changeState(RobotState.NORMAL_SLEEPING);
                        } else {
                            moveTowards(destination_floor);
                        }
                    } else {
                        moveTowards(destination_floor);
                    }
                }
                break;
            case WRAPPING:
                if (wrapping_time<2){
                    changeState(RobotState.WRAPPING);
                }
                else {
                    changeState(RobotState.DELIVERING_FRAGILE);
                }
                break;

            case FRAGILE_SLEEPING:
                if (occupying_floot.containsKey(current_floor + 1)||occupying_floot.containsKey(current_floor - 1)){//下一层或者上一层被占用
                    changeState(RobotState.FRAGILE_SLEEPING);
                } else {
                    //moveTowards(destination_floor);
                    changeState(RobotState.DELIVERING_FRAGILE);
                }
                break;

            case NORMAL_SLEEPING:
                if (occupying_floot.containsKey(current_floor + 1)) {
                    if (occupying_floot.get(current_floor + 1).equals(FloorState.FRAGILEITEM)) {
                        changeState(RobotState.NORMAL_SLEEPING);
                    } else {
                        changeState(RobotState.DELIVERING);
                    }
                } else if (occupying_floot.containsKey(current_floor - 1)) {
                    if (occupying_floot.get(current_floor - 1).equals(FloorState.FRAGILEITEM)) {
                        changeState(RobotState.NORMAL_SLEEPING);
                    } else {
                        changeState(RobotState.DELIVERING);
                    }
                } else {
                    changeState(RobotState.DELIVERING);
                }
                break;

            case UNWRAPPING:
                delivery.deliver(fragileItem);
                fragileItem = null;
                occupying_floot.remove(current_floor);
                deliveryCounter++;
                if (deliveryCounter > 3) {  // Implies a simulation bug
                    throw new ExcessiveDeliveryException();
                }
                if (deliveryItem != null) {
                    setRoute();
                    changeState(RobotState.DELIVERING);
                }
                else {
                    changeState(RobotState.RETURNING);
                }
                break;
            case DELIVERING_FRAGILE:
                if(current_floor == destination_floor) {// If already here drop off either way
                    occupying_floot.put(destination_floor,FloorState.FRAGILEITEM);//占用这个楼层
                    changeState(RobotState.UNWRAPPING);
                }
                else {
                    /** The robot is not at the destination yet, move towards it! */

                        if (occupying_floot.containsKey(current_floor + 1)||occupying_floot.containsKey(current_floor - 1)){//下一层或者上一层被占用
                            changeState(RobotState.FRAGILE_SLEEPING);
                        }
                        else {
                        moveTowards(destination_floor);
                    }
                }
                break;
            default:

    	}
    }

    /**
     * Sets the route for the robot
     */
    private void setRoute() {
        /** Set the destination floor */
        if(this.fragileItem!=null){
            destination_floor = fragileItem.getDestFloor();
        }
        else {
            destination_floor = deliveryItem.getDestFloor();
        }

    }

    /**
     * Generic function that moves the robot towards the destination
     * @param destination the floor towards which the robot is moving
     */
    private void moveTowards(int destination) {
        try{
            if(current_floor < destination){
                current_floor++;
            } else {
                current_floor--;
            }
            if (this.fragileItem!=null){//fragile的货物还没运完
                System.out.printf("T: %3d > %9s-> CURRENT_FLOOR_:"+this.current_floor+"  [%s]%n", Clock.Time(), getIdTube(), fragileItem.toString());
            }
            else if (deliveryItem!=null){
                System.out.printf("T: %3d > %9s-> CURRENT_FLOOR_:" +this.current_floor+" [%s]%n", Clock.Time(), getIdTube(), deliveryItem.toString());
            }
        }catch (NullPointerException e){
            e.printStackTrace();
        }

    }
    
    private String getIdTube() {
    	return String.format("%s(%1d,%1d )", id, (tube == null ? 0 : 1),(fragileItem == null ? 0 : 1));//tube为0时代表无邮件在管道里，1为有
    }
    
    /**
     * Prints out the change in state
     * @param nextState the state to which the robot is transitioning
     */
    private void changeState(RobotState nextState){
    	assert(!(deliveryItem == null && tube != null));
    	if (current_state != nextState) {
            System.out.printf("T: %3d > %7s changed from %s to %s%n", Clock.Time(), getIdTube(), current_state, nextState);
    	}
    	current_state = nextState;
    	if(nextState == RobotState.DELIVERING_FRAGILE){
            System.out.printf("T: %3d > %9s-> DELIVERING FRAGILE:  [%s]%n", Clock.Time(), getIdTube(), fragileItem.toString());
        }
    	if(nextState == RobotState.DELIVERING){
    	    System.out.printf("T: %3d > %9s-> DELIVERING:  [%s]%n", Clock.Time(), getIdTube(), deliveryItem.toString());
    	}
    	if (nextState == RobotState.WRAPPING){
    	    wrapping_time = wrapping_time+1;
            System.out.printf("T: %3d > %9s-> WARPPING Time: "+wrapping_time+": [%s]%n", Clock.Time(), getIdTube(), fragileItem.toString());
        }
        if (nextState == RobotState.UNWRAPPING){
            System.out.printf("T: %3d > %9s-> UNWARPPING : [%s]%n", Clock.Time(), getIdTube(), fragileItem.toString());
        }
        if (nextState == RobotState.FRAGILE_SLEEPING){
            System.out.println(id+" is waiting for release");
            //System.out.printf("T: %3d > %9s-> SLEEPING : [%s]%n", Clock.Time(), getIdTube(), fragileItem.toString());

        }
    }

	public MailItem getTube() {
		return tube;
	}
    
	static private int count = 0;
	static private Map<Integer, Integer> hashMap = new TreeMap<Integer, Integer>();

	@Override
	public int hashCode() {
		Integer hash0 = super.hashCode();
		Integer hash = hashMap.get(hash0);
		if (hash == null) { hash = count++; hashMap.put(hash0, hash); }
		return hash;
	}

	public boolean isEmpty() {
		return (deliveryItem == null && tube == null && fragileItem ==null);
	}

	public void addToHand(MailItem mailItem) throws ItemTooHeavyException, BreakingFragileItemException {
		assert(deliveryItem == null);
		if(mailItem.fragile) {
            System.out.println("throw new BreakingFragileItemException : Add to hand: " + mailItem.id + " is a fragile item!!");
            //throw new BreakingFragileItemException();
        }
		deliveryItem = mailItem;
		if (deliveryItem.weight > INDIVIDUAL_MAX_WEIGHT) {
            throw new ItemTooHeavyException();
        }
	}

	public void addToTube(MailItem mailItem) throws ItemTooHeavyException, BreakingFragileItemException {
		assert(tube == null);
		if(mailItem.fragile) {
          System.out.println("throw new BreakingFragileItemException: Add to tube: "+mailItem.id + " is a fragile item!!");
            //throw new BreakingFragileItemException();
        }
		tube = mailItem;
		if (tube.weight > INDIVIDUAL_MAX_WEIGHT) {
            throw new ItemTooHeavyException();
        }
	}

    public void addToSpecialArms(MailItem mailItem)  throws ItemTooHeavyException{
	    assert (fragileItem == null);
        fragileItem = mailItem;
        if (fragileItem.weight > INDIVIDUAL_MAX_WEIGHT) {
            throw new ItemTooHeavyException();
        }
    }

}
