package main;

import java.util.List;
import java.util.Set;

import entities.Node;

//TODO: Clean up getTextDirections (see notes)
/*
Notes:

fromPath should probably return a List<String>, which can be displayed as desired.

getTextDirections could use some dramatic cleanup; if IntelliJ says it is too complex to
  analyze, it's probably too complex

The actual directions "left", "right", etc. should probably be an Enum.

 */

public class DirectionsGenerator
{

	private static int rightMin1 = 345;
	private static int rightMax1 = 360;
	private static int rightMin2 = 0;
	private static int rightMax2 = 15;

	private static int softRightMax = 75;
	private static int straightMax = 105;
	private static int softLeftMax = 165;
	private static int leftMax = 195;
	private static int hardLeftMax = 265;
	private static int backWardsMax = 275;
	private static int hardRightMax = 345;

	/**
	 * Generate text directions based on the given path.
	 *
	 * @param path A list of nodes representing the path.
	 *
	 * @return Directions for the path, as a string.
	 */
	public static String fromPath(List<Node> path) {
		Node[] asArray = new Node[path.size()];
		int i = 0;
		for (Node n : path) {
			asArray[i] = n;
			i++;
		}
		return DirectionsGenerator.getTextDirections(asArray);
	}

	// TODO: Use StringBuilder instead of String concatenation
	/** Returns a string that tells how to get from start to end
	 *
	 * @param path the nodes along the path
	 * @return String directions that tell how to reach a destination
	 */
	private static String getTextDirections(Node[] path) {
		String directions = "First, ";

		int leftTurns = 0, rightTurns = 0;
		if (path.length > 1 && isElevator(path[0], path[1])) {
			directions += "Take the elevator to the " + path[1].getFloor() + getTurnPostfix(path[1].getFloor()) + " floor\nThen ";
		}
		for(int i = 1; i < path.length - 1; i++) {
			// TODO: These were somehow reversed, but that didn't make sense so we need to figure out why
			// During testing, this method worked how we wanted, but when implementing this code, turns were reversed. (right turns were left turns)
			double turnAngle = path[i].angle(path[i+1], path[i-1]);

			// Determine the direction of the turn through a bunch of if statements that check which direction the path is traveling
			if (isElevator(path[i],path[i+1])){
				directions += "go straight and take the elevator to the " + path[i+1].getFloor()
						+ getTurnPostfix(path[i+1].getFloor()) + " floor\nThen ";
			}
			else if(isRightTurn(turnAngle)) {
				// Right Turn
				if(rightTurns == 0) {
					directions += "take a right turn,\nThen ";
				} else {
					rightTurns++;
					directions += "continue straight and take the " + rightTurns + getTurnPostfix(rightTurns) +  " right,\nThen ";
				}
				// if you take a turn, then the count for turns should be reset to 0
				rightTurns = 0;
				leftTurns = 0;
			} else if(isSoftRightTurn(turnAngle)) {
				// Soft Right Turn
				directions += "take a soft right turn,\nThen ";
				// if you take a turn, then the count for turns should be reset to 0
				leftTurns = 0;
				rightTurns = 0;
			} else if(isStraight(turnAngle)) {
				// Straight (NO TURN!!!)
//				directions += "continue straight,\nThen "; // we don't want to spam them with this
				// Figure out if there is a left or right turn available as well, then increment the counters
				Set<Node> forks = path[i].getNeighbors();
				for(Node fork : forks) {
					// TODO: This was also reversed, similar to above
					int forkAngle = (int) path[i].angle(fork, path[i-1]);

					if(isRightTurn(forkAngle) || isSoftRightTurn(forkAngle) || isHardRightTurn(forkAngle)) {
						rightTurns++;
					}
					if(isLeftTurn(forkAngle) || isSoftLeftTurn(forkAngle) || isHardLeftTurn(forkAngle)) {
						leftTurns++;
					}
				}
			} else if(isSoftLeftTurn(turnAngle)) {
				// Soft Left Turn
				directions += "take a soft left turn\nThen ";
				// if you take a turn, then the count for turns should be reset to 0
				leftTurns = 0;
				rightTurns = 0;
			} else if(isLeftTurn(turnAngle)) {
				// Left Turn
				if(leftTurns == 0) {
					directions += "take a left turn,\nThen ";
				} else {
					leftTurns++;
					directions += "continue straight and take the " + leftTurns + getTurnPostfix(leftTurns) + " left,\nThen ";
				}
				// if you take a turn, then the count for turns should be reset to 0
				leftTurns = 0;
				rightTurns = 0;
			} else if(isHardLeftTurn(turnAngle)) {
				// Hard Left Turn
				directions += "take a hard left turn\nThen ";
				// if you take a turn, then the count for turns should be reset to 0
				leftTurns = 0;
				rightTurns = 0;
			} else if(isBackwards(turnAngle)) {
				// Turn Around
				directions += "turn around\nThen ";
				// if you take a turn, then the count for turns should be reset to 0
				leftTurns = 0;
				rightTurns = 0;
			} else if(isHardRightTurn(turnAngle)) {
				// Hard Right Turn
				directions += "take a hard right turn\nThen ";
				// if you take a turn, then the count for turns should be reset to 0
				leftTurns = 0;
				rightTurns = 0;
			}
		}
		directions += "you are at your destination.";
		return directions;
	}

	/** Determines if the angle given corresponds to a right turn
	 *
	 * @param angle the turn angle
	 * @return true: if it's a right turn
	 *         false: otherwise
	 */
	private static boolean isRightTurn(double angle) {
		return isBetween(rightMin1, angle, rightMax1) || isBetween(rightMin2, angle, rightMax2);
	}

	/** Determines if the angle given corresponds to a left turn
	 *
	 * @param angle the turn angle
	 * @return true: if it's a left turn
	 *         false: otherwise
	 */
	private static boolean isLeftTurn(double angle) {
		return isBetween(softLeftMax, angle, leftMax);
	}

	/** Determines if the angle given corresponds to a soft right turn
	 *
	 * @param angle the turn angle
	 * @return true: if it's a soft right turn
	 *         false: otherwise
	 */
	private static boolean isSoftRightTurn(double angle) {
		return isBetween(rightMax2, angle, softRightMax);
	}

	/** Determines if the angle given corresponds to a soft left turn
	 *
	 * @param angle the turn angle
	 * @return true: if it's a soft left turn
	 *         false: otherwise
	 */
	private static boolean isSoftLeftTurn(double angle) {
		return isBetween(straightMax, angle, softLeftMax);
	}

	/** Determines if the angle given corresponds to a hard right turn
	 *
	 * @param angle the turn angle
	 * @return true: if it's a hard right turn
	 *         false: otherwise
	 */
	private static boolean isHardRightTurn(double angle) {
		return isBetween(backWardsMax, angle, hardRightMax);
	}

	/** Determines if the angle given corresponds to a hard left turn
	 *
	 * @param angle the turn angle
	 * @return true: it it's a hard left turn
	 *         false: otherwise
	 */
	private static boolean isHardLeftTurn(double angle) {
		return isBetween(leftMax, angle, hardLeftMax);
	}

	/** Determines if the angle given corresponds to moving straight
	 *
	 * @param angle the turn angle
	 * @return true: if it's a straight
	 *         false: otherwise
	 */
	private static boolean isStraight(double angle) {
		return isBetween(softRightMax, angle, straightMax);
	}

	/** Determines if the angle given corresponds to turning around
	 *
	 * @param angle the turn angle
	 * @return true: if it's backwards
	 *         false: otherwise
	 */
	private static boolean isBackwards(double angle) {
		return isBetween(hardLeftMax, angle, backWardsMax);
	}

	/** Determines is B is numerically between A and C
	 * A should be less than C
	 * @param A the lesser bound
	 * @param B the number you want to check to be in between A and C
	 * @param C the greater bound
	 * @return
	 */
	private static boolean isBetween(double A, double B, double C) {
		return B >= A && B < C;
	}

	/** Determines whether the user is going to use an elevator of not
	 * @param node1 the start node
	 * @param node2 the end node
	 * @return true: if they are on the same floor
	 *         false: if they are on the different floor
	 */
	private static boolean isElevator(Node node1, Node node2){
		if (node1.getFloor() != node2.getFloor()) {
			return true;
		}
		else{
			return false;
		}
	}

	/** Gives the postfix for a number
	 *
	 * @param turns the number that needs a postfix
	 * @return "st" if turns == 1, "nd" if turns == 2, "rd" if turns == 3, "th" if turns == 4, 5, 6, 7, 8,9, 10, 11, 12, 13, 14, 15, 16, 17, 18,19,20, and potentially so on...
	 */
	private static String getTurnPostfix(int turns) {
		// This does not account for numbers greater than 20
		switch(turns) {
			case 1:
				return "st";
			case 2:
				return "nd";
			case 3:
				return "rd";
			default:
				return "th";
		}
	}
}