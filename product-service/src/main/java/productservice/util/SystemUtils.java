package productservice.util;

import java.util.Collection;

public class SystemUtils {
    public static boolean isEmptyString(String arg){
        return arg == null || arg.isBlank();
    }

    public static boolean isEmptyCollection(Collection<?> arg){
        return arg == null || arg.isEmpty();
    }


}
