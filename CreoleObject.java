import java.util.ArrayList;
import java.lang.reflect.Method;



class CreoleObject extends Thread {
  private ArrayList<CreoleCall> calls = new ArrayList<CreoleCall>();
  private ArrayList<CreoleCall> suspended = new ArrayList<CreoleCall>(); // tasks that volunarily suspended
  private CreoleCall current = null;
  CreoleObject() {
    this.start();
  }
  
  synchronized Future invoke(String method, Object... args) {
    Future fut = new Future();
    CreoleCall newCall = new CreoleCall(method, fut, args);
    calls.add(newCall);
    System.out.println("new call added " + this.getClass() + " " +method);
    notify();
    return fut;
  }
  
  synchronized void invokeVoid(String method, Object... args) {
    CreoleCall newCall = new CreoleCall(method, null, args);
    calls.add(newCall);
    System.out.println("new call added " + this.getClass() + " " + method);
    notify();
  }
  
  Object creoleAwait(Future fut) {
    // busy waits only if nothing else to do
    while(!fut.ready) {
      creoleSuspend();
    }
    return fut.get();
  }
  
  void creoleSuspend() {
    CreoleCall suspendee;
    // first put it in the suspended queue and mark this object as not busy, waking up the dispatcher
    synchronized(this) {
      assert(current!=null);
      suspendee = current;
      suspended.add(current);
      current = null; // no longer busy
      notify(); 
    }

    // now actully put this thread to sleep but make sure it didn't get woken up immediately by the dispatcher
    synchronized(suspendee) {
      if (!suspendee.wakingUp) {
        try {
          //System.out.println("suspending");
          suspendee.wait();
        }
        catch (InterruptedException e) {
          e.printStackTrace(System.out);
        }
      }
      else {
        //System.out.println("wake up before suspending");
      }
      suspendee.wakingUp = false;
    }
  }
  
  public void run() {
    try {
      while (true) {
        // see if any new calls to process
        if(calls.size() > 0) {
          CreoleCall call;
          synchronized (this) {
            call = calls.remove(0);
            current = call; // now busy
          }
          call.start();
        }
        // no calls, what about any suspended calls
        else if (suspended.size() > 0) {
          CreoleCall call;
          synchronized (this) {
            call = suspended.remove(0);
            current = call; // now busy
          }
          synchronized (call) {
            call.wakingUp = true;
            call.notify();
          }
        }
        // nothing to do but wait
//        else {
//          synchronized (this) {
//            // something may have come in since last check so make sure no calls before going to sleep
//            if (calls.size() == 0) {
//              System.out.println(this.getClass() + " waiting no calls.");
//              wait();
//            }
//          }
//        }
        // If there is an active call or nothing to do, just wait
        // need to recheck the two queue lengths because something could have come in since checked above
        synchronized (this) {
          if (current != null || (calls.size() == 0 && suspended.size() == 0)) {
            System.out.println(this.getClass() + " waiting a call is active.");
            wait();
          }
        }
      }
    }
    catch (InterruptedException e) {
      System.out.println(this + " exiting");
    }
  }
  
  // inner class because every call must be associated with some CreoleObject
  class CreoleCall extends Thread {
    String method;
    Future fut;
    Object[] args;
    boolean wakingUp = false;
    CreoleCall(String method, Future fut, Object... args) {
      this.method = method;
      this.fut = fut;
      this.args = args;
    }
    
    public void run() {
      invoke();
      // call is over - notify the dispatcher that it can schedule another
      synchronized (CreoleObject.this) {
        current = null; // no longer busy
        CreoleObject.this.notify();
      }
    }
    private void invoke() {
      System.out.println("processing call " + this.getClass() + " " + method);
      Method m = null;
      try {
        // create array of classes representing the types of the args
        Class[] types = new Class[args.length];
        for(int i = 0; i < args.length; i++) {
          types[i] = args[i].getClass();
        }
        m = CreoleObject.this.getClass().getMethod(method,types);
      }
      catch (NoSuchMethodException e) {
        System.out.println(e);
      }
      if (m != null) {
        try {
          if (fut != null) {
            Object result = m.invoke(CreoleObject.this,args);
            fut.set(result);
            System.out.println("Setting future " + this.getClass() + " " + method);
          }
          else {
            m.invoke(CreoleObject.this,args);
            System.out.println("end of void method " + this.getClass() + " " + method);
          }
        }
        catch (Exception e) {
          e.printStackTrace(System.out);
        }
      }
    }
  }
  
  
}