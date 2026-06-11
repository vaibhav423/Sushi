// Example: Test variable operations
import java.util.*;

bridge.setVar("testVar", "Hello from BeanShell!");
bridge.setVar("number", 42);

List myList = new ArrayList();
myList.add("Item 1");
myList.add("Item 2");
myList.add("Item 3");
bridge.setVar("myList", myList);

String testVar = (String) bridge.getVar("testVar");
Integer number = (Integer) bridge.getVar("number");

bridge.log("testVar = " + testVar);
bridge.log("number = " + number);
bridge.log("myList size = " + myList.size());

bridge.toast("Variables set: " + testVar);

return "testVar=" + testVar + " number=" + number + " listSize=" + myList.size();
