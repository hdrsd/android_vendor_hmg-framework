package dev.stinger.split.test;
/** Exit after the CLI returns: vendor framework startup otherwise leaves non-daemon threads behind. */
public final class CommandRunner {
    public static void main(String[] args) {
        int result=0;
        try {
            String tool=args[0],cls;
            if(tool.equals("am"))cls="com.android.commands.am.Am";
            else if(tool.equals("pm"))cls="com.android.commands.pm.Pm";
            else if(tool.equals("input"))cls="com.android.commands.input.Input";
            else throw new IllegalArgumentException("Unexpected tool");
            String[] rest=new String[args.length-1];System.arraycopy(args,1,rest,0,rest.length);
            Class.forName(cls).getMethod("main",String[].class).invoke(null,(Object)rest);
        } catch(Throwable t){t.printStackTrace(System.out);result=1;}
        System.exit(result);
    }
}
