package automail;

import exceptions.BreakingFragileItemException;
import exceptions.ExcessiveDeliveryException;
import exceptions.ItemTooHeavyException;
import exceptions.MailAlreadyDeliveredException;
import strategies.Automail;
import strategies.IMailPool;
import strategies.MailPool;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

/**
 * This class simulates the behaviour of AutoMail
 */
public class Simulation {
    /** Constant for the mail generator */
    private static int MAIL_TO_CREATE;
    private static int MAIL_MAX_WEIGHT;
    
    private static boolean CAUTION_ENABLED;
    private static boolean FRAGILE_ENABLED;
    private static boolean STATISTICS_ENABLED;
    
    private static ArrayList<MailItem> MAIL_DELIVERED;
    private static double total_score = 0;

	/** statictics */
    public static int deliver_normally =0;
	public static int deliver_caution = 0;
	public static int weight_deliver_normally = 0;
	public static int weight_deliver_caution = 0;
	public static int wapping_unwapping_time = 0;

    public static void main(String[] args) throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException {
    	Properties automailProperties = new Properties();
		// Default properties
    	automailProperties.setProperty("Robots", "Standard");
    	automailProperties.setProperty("MailPool", "strategies.SimpleMailPool");
    	automailProperties.setProperty("Floors", "10");
    	automailProperties.setProperty("Mail_to_Create", "80");
    	automailProperties.setProperty("Last_Delivery_Time", "100");
    	automailProperties.setProperty("Caution", "false");
    	automailProperties.setProperty("Fragile", "false");
    	automailProperties.setProperty("Statistics", "false");

    	// Read properties
		FileReader inStream = null;
		try {
			inStream = new FileReader("automail.properties");
			automailProperties.load(inStream);
		} finally {
			 if (inStream != null) {
	                inStream.close();
	            }
		}

		//Seed
		String seedProp = automailProperties.getProperty("Seed");
		// Floors
		Building.FLOORS = Integer.parseInt(automailProperties.getProperty("Floors"));
        System.out.println("Floors: " + Building.FLOORS);
		// Mail_to_Create
		MAIL_TO_CREATE = Integer.parseInt(automailProperties.getProperty("Mail_to_Create"));
        System.out.println("Mail_to_Create: " + MAIL_TO_CREATE);
        // Mail_to_Create
     	MAIL_MAX_WEIGHT = Integer.parseInt(automailProperties.getProperty("Mail_Max_Weight"));
        System.out.println("Mail_Max_Weight: " + MAIL_MAX_WEIGHT);
		// Last_Delivery_Time
		Clock.LAST_DELIVERY_TIME = Integer.parseInt(automailProperties.getProperty("Last_Delivery_Time"));
        System.out.println("Last_Delivery_Time: " + Clock.LAST_DELIVERY_TIME);
        // Caution ability
        CAUTION_ENABLED = Boolean.parseBoolean(automailProperties.getProperty("Caution"));
        System.out.println("Caution enabled: " + CAUTION_ENABLED);
        // Fragile mail generation
        FRAGILE_ENABLED = Boolean.parseBoolean(automailProperties.getProperty("Fragile"));
        System.out.println("Fragile enabled: " + FRAGILE_ENABLED);
        // Statistics tracking
        STATISTICS_ENABLED = Boolean.parseBoolean(automailProperties.getProperty("Statistics"));
        System.out.println("Statistics enabled: " + STATISTICS_ENABLED);
		// Robots
		int robots = Integer.parseInt(automailProperties.getProperty("Robots"));
		System.out.print("Robots: "); System.out.println(robots);
		assert(robots > 0);
		// MailPool
		IMailPool mailPool = new MailPool(robots);

		// End properties
		
        MAIL_DELIVERED = new ArrayList<MailItem>();
                
        /** Used to see whether a seed is initialized or not */
        HashMap<Boolean, Integer> seedMap = new HashMap<>();
        
        /** Read the first argument and save it as a seed if it exists */
        if (args.length == 0 ) { // No arg
        	if (seedProp == null) { // and no property
        		seedMap.put(false, 0); // so randomise
        	} else { // Use property seed
        		seedMap.put(true, Integer.parseInt(seedProp));
        	}
        } else { // Use arg seed - overrides property
        	seedMap.put(true, Integer.parseInt(args[0]));
        }
        Integer seed = seedMap.get(true);
        System.out.println("Seed: " + (seed == null ? "null" : seed.toString()));

        Automail automail = new Automail(mailPool, new ReportDelivery(), robots);
        MailGenerator mailGenerator = new MailGenerator(MAIL_TO_CREATE, MAIL_MAX_WEIGHT, automail.mailPool, seedMap);
        
        /** Initiate all the mail */
        mailGenerator.generateAllMail(FRAGILE_ENABLED);//根据seed生成所有的邮件
        while(MAIL_DELIVERED.size() != mailGenerator.MAIL_TO_CREATE) {//运送过程还没结束
			//System.out.println("MAIL_DELIVERED: "+ MAIL_DELIVERED.size());
			//System.out.println("MAIL_TO_CREATE: "+mailGenerator.MAIL_TO_CREATE);
			//System.out.println("CurrentClockTime："+ Clock.Time());
			/** Add the mail that matches the current moment to the mailpool */
            mailGenerator.step();//将符合现在时刻的mail加入到mailpool中
            try {
            	/** Assign tasks to each robot */
                automail.mailPool.step();//给每个机器人分配任务
				for (int i=0; i<robots; i++) {
					//System.out.println("for");
					/** Let each robot proceed to the next step according to the current state */
					automail.robots[i].step();//让每个机器人按当前状态进行下一个步骤
					//System.out.println("Robot "+ i + " are currently in floor: "+ automail.robots[i].current_floor+automail.robots[i].current_state);
				}
			} catch (ExcessiveDeliveryException|ItemTooHeavyException|BreakingFragileItemException e) {
				e.printStackTrace();
				System.out.println("Simulation unable to complete.");
				System.exit(0);
			}
            Clock.Tick();
        }
        printResults();
        if (STATISTICS_ENABLED){
			printStatistic();
		}

    }
    
    static class ReportDelivery implements IMailDelivery {
    	
    	/** Confirm the delivery and calculate the total score */
    	@Override
		public void deliver(MailItem deliveryItem){
    		if(!MAIL_DELIVERED.contains(deliveryItem)){
    			MAIL_DELIVERED.add(deliveryItem);
                System.out.printf("T: %3d > Deliv(%4d) [%s]%n", Clock.Time(), MAIL_DELIVERED.size(), deliveryItem.toString());
    			// Calculate delivery score
    			total_score += calculateDeliveryScore(deliveryItem);
    		}
    		else{
    			try {
    				throw new MailAlreadyDeliveredException();
    			} catch (MailAlreadyDeliveredException e) {
    				e.printStackTrace();
    			}
    		}
    	}

    }
    
    private static double calculateDeliveryScore(MailItem deliveryItem) {
    	// Penalty for longer delivery times
    	final double penalty = 1.2;
    	double priority_weight = 0;
        return Math.pow(Clock.Time() - deliveryItem.getArrivalTime(),penalty)*(1+Math.sqrt(priority_weight));
    }

    public static void printResults(){
        System.out.println("T: "+Clock.Time()+" | Simulation complete!");
        System.out.println("Final Delivery time: "+Clock.Time());
        System.out.printf("Final Score: %.2f%n", total_score);

    }

    public static void printStatistic(){
			System.out.println("The number of packages delivered normally: "+ deliver_normally);
			System.out.println("The number of packages delivered using caution: "+ deliver_caution);
			System.out.println("The total weight of the packages delivered normally: "+ weight_deliver_normally);
			System.out.println("The total weight of the packages delivered using caution: " + weight_deliver_caution);
			System.out.println("The total amount of time spent by the special arms wrapping & unwrapping items: "+ wapping_unwapping_time);
	}

}
