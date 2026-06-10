// Example: Query installed packages
import android.content.pm.*;
import java.util.*;

PackageManager pm = context.getPackageManager();
List<PackageInfo> packages = pm.getInstalledPackages(0);

// Build list of package names
List<String> packageNames = new ArrayList();
for (PackageInfo pkg : packages) {
    packageNames.add(pkg.packageName);
}

// Sort and limit to first 10
Collections.sort(packageNames);
int count = Math.min(10, packageNames.size());
List<String> firstFew = packageNames.subList(0, count);

bridge.log("Total packages: " + packages.size());
bridge.log("First " + count + ": " + firstFew);

bridge.toast("Found " + packages.size() + " packages");

// Return as JSON
return bridge.toJson(firstFew);
