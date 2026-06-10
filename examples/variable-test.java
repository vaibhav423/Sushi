// Example: Test variable operations
import java.util.*;

// Set variables
bridge.setVar("testVar", "Hello from BeanShell!");
bridge.setVar("number", 42);

// Create a list
List<String> myList = new ArrayList();
myList.add("Item 1");
myList.add("Item 2");
myList.add("Item 3");

bridge.setVar("myList", myList);

// Get variables back
String testVar = (String) bridge.getVar("testVar");
Integer number = (Integer) bridge.getVar("number");

// Log and show toast
bridge.log("testVar = " + testVar);
bridge.log("number = " + number);
bridge.log("myList size = " + myList.size());

bridge.toast("Variables set: " + testVar);

// Return JSON representation
return bridge.toJson(bridge.getVars());
