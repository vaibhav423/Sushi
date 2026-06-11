// Example: Query installed packages
import android.content.pm.*;
import java.util.*;

PackageManager pm = context.getPackageManager();
List packages = pm.getInstalledPackages(0);

List names = new ArrayList();
for (Object p : packages) {
    PackageInfo pkg = (PackageInfo) p;
    names.add(pkg.packageName);
}

Collections.sort(names);
int count = Math.min(10, names.size());
List firstFew = names.subList(0, count);

bridge.log("Total packages: " + packages.size());
bridge.log("First " + count + ": " + firstFew);
bridge.toast("Found " + packages.size() + " packages");

return "Total: " + packages.size() + ", First few: " + firstFew;