package strategies;

import java.util.LinkedList;
import java.util.Comparator;
import java.util.ListIterator;

import automail.MailItem;
import automail.Robot;
import exceptions.BreakingFragileItemException;
import exceptions.ItemTooHeavyException;

public class MailPool implements IMailPool {

    private class Item {
        int destination;
        MailItem mailItem;

        public Item(MailItem mailItem) {
            destination = mailItem.getDestFloor();
            this.mailItem = mailItem;
        }
    }

    public class ItemComparator implements Comparator<Item> {
        @Override
        public int compare(Item i1, Item i2) {
            int order = 0;
            if (i1.destination > i2.destination) {  // Further before closer
                order = 1;
            } else if (i1.destination < i2.destination) {
                order = -1;
            }
            return order;
        }
    }

    private LinkedList<Item> pool;
    private LinkedList<Item> fragile_pool;
    private LinkedList<Item> unfragile_pool;
    private LinkedList<Robot> robots;

    public MailPool(int nrobots) {
        // Start empty
        pool = new LinkedList<Item>();
		fragile_pool = new LinkedList<Item>();
        unfragile_pool = new LinkedList<Item>();
        robots = new LinkedList<Robot>();
    }

    @Override
	public void addToPool(MailItem mailItem) {
		Item item = new Item(mailItem);
		pool.add(item);
		pool.sort(new ItemComparator());
	}

	@Override
	public void addToFragilePool(MailItem mailItem) {
		Item item = new Item(mailItem);
		fragile_pool.add(item);
		fragile_pool.sort(new ItemComparator());
	}
    @Override
    public void addToUnFragilePool(MailItem mailItem) {
        Item item = new Item(mailItem);
        unfragile_pool.add(item);
        unfragile_pool.sort(new ItemComparator());
    }

    @Override
    public void step() throws ItemTooHeavyException, BreakingFragileItemException {
        try {
            ListIterator<Robot> i = robots.listIterator();
            while (i.hasNext()) {
                loadRobot(i);
            }
        } catch (Exception e) {
            throw e;
        }
    }

    //给相应的机器人分配任务
	private void loadRobot(ListIterator<Robot> i) throws ItemTooHeavyException, BreakingFragileItemException {
        Robot robot = i.next();
        assert(robot.isEmpty());
		// System.out.printf("P: %3d%n", pool.size());
		ListIterator<Item> unf_i = unfragile_pool.listIterator();
        ListIterator<Item> f_i = fragile_pool.listIterator();
        /*ListIterator<Item> j = pool.listIterator();
        if (pool.size() > 0) {
            try {
                robot.addToHand(j.next().mailItem); // hand first as we want higher priority delivered first
                j.remove();
                if (pool.size() > 0) {
                    robot.addToTube(j.next().mailItem);
                    j.remove();
                }
                robot.dispatch(); // send the robot off if it has any items to deliver
                i.remove();       // remove from mailPool queue
            } catch (Exception e) {
                throw e;
            }
        }*/


		if (unfragile_pool.size() > 0 && fragile_pool.size() > 0) {
            //System.out.println("都有");
			try {
				robot.addToHand(unf_i.next().mailItem); // hand first as we want higher priority delivered first
				unf_i.remove();
				if (unfragile_pool.size() > 0) {
					robot.addToTube(unf_i.next().mailItem);
					unf_i.remove();
				}
				robot.addToSpecialArms(f_i.next().mailItem);
				f_i.remove();
				robot.dispatch(); // send the robot off if it has any items to deliver
				i.remove();       // remove from mailPool queue
			} catch (Exception e) {
				throw e;
			}
		}
		else if (unfragile_pool.size() > 0){
           // System.out.println("un");
            try {
                robot.addToHand(unf_i.next().mailItem); // hand first as we want higher priority delivered first
                unf_i.remove();
                if (unfragile_pool.size() > 0) {
                    robot.addToTube(unf_i.next().mailItem);
                    unf_i.remove();
                }
                robot.dispatch(); // send the robot off if it has any items to deliver
                i.remove();       // remove from mailPool queue
            }catch (IllegalStateException e1){
                e1.printStackTrace();
            }
            catch (Exception e2) {
                throw e2;
            }
        }
		else if (fragile_pool.size() > 0){
            //System.out.println("fra");
		    try {
                robot.addToSpecialArms(f_i.next().mailItem);
                f_i.remove();
                robot.dispatch(); // send the robot off if it has any items to deliver
                i.remove();       // remove from mailPool queue
            }
            catch (Exception e) {
                throw e;
            }
        }
		else {
            //System.out.println("都他娘没有");
        }
	}

    @Override
    public void registerWaiting(Robot robot) { // assumes won't be there already
        robots.add(robot);
    }

}
