our goal is that: in Java or Android Environment, we could implement all features that vidalia have.

the package 'jtorctl' is rather old, made a new one for communicating between java app and tor process.the design of this version uses the official C++ control lib (in vidalia? i forgot) as a reference source; so it's more powerful than original 'jtorctl'.

for start or terminate a tor process, look at class "net.sanpei.tor.Controller". some codes for example:
```
....
controller = new Controller(exe_path, cfg_path);
controller.attachOrStart();
.....
controller.stopProcess();
```

for operating a tor process, look at class "net.sanpei.tor.Operator" for usage. some codes for example:
```
import net.sanpei.tor.EventDispatcher;
import net.sanpei.tor.Operator;
import net.sanpei.tor.entities.Bootstrap;

import android.os.Handler;

....

private Operator operations;
operations = new Operator(port, path_to_config);

.....

operations.connect();

...

myHandler = new Handler();
EventDispatcher.Handler bootstrapHandler = new EventDispatcher.Handler() {
    @Override
    public void bootstrapStatusChanged(Bootstrap bs) {
        final Bootstrap local = bs;
        myHandler.post(new Runnable() {
            public void run() {
                //notify UI asynchronously
            }    
        });
    }
    //we can override other method to receive different events
};
//receive bootstrap message
operations.addEventHandler(bootstrapHandler);

//get bootstrap info and fire handler manually
Bootstrap bs = operations.getBootstrap();
bootstrapHandler.bootstrapStatusChanged(bs);

....

operations.newIdentity();
operations.refreshTor();

...

SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
final boolean mode = !sp.getBoolean(Prefs.TOR_USE_BRIDGES, false);
String bridgesText = sp.getString(Prefs.TOR_BRIDGES, "");
String[] bridges = bridgesText.split("\\s+");								
operations.useBridges(mode, bridges);		

....

operations.removeEventHandler(bootstrapHandler);

....

operations.cleanEventHandlers();
operations.disconnect();
```

classes for most of info objects were implemented. for actions of getting info from tor process and creating these objects, only bootstrap object fetching was implemented at current. people who want more could easily implement them with Connection and Command class, you could take a look at Operator class and the get method of Bootstrap class for how to do.

and if you want to commit your works, email me plz.