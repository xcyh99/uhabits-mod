package android.view;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Compile-time signature for the platform hidden RemoteViews annotation.
 * This module is a compileOnly dependency and is not packaged in the APK.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RemotableViewMethod {
    String asyncImpl() default "";
}
