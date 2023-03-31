/*_____________________________________________________________
MPK.sc
MIDI control with the Akai MPK mini MKII

(C) 2022 Jonathan Reus
http://jonathanreus.com

-------------------------------------------------------

@usage

MPKmini.init(verbose: true, mpkPreset: 4); // initialize with factory preset 4

MPKmini.keyAction = {|note, vel, msg| [note,vel,msg].postln }; // change keyboard responder

MPKmini.knobAction = {|knob, val| [knob,val].postln }; // change knob responder

MPKmini.xyAction = {|xpos, ypos| [xpos, ypos].postln }; // change joystick responder

MPKmini.padNoteActionA = {|pad, vel, msg| [\A, pad, vel, msg].postln }; // change bank A pad responder (Note mode)

MPKmini.padNoteActionB = {|pad, vel, msg| [\B, pad, vel, msg].postln }; // change bank B pad responder (Note mode)

MPKmini.padCCActionA = {|pad, vel, msg| [\A, pad, vel, msg].postln }; // change bank A pad responder (CC mode)

MPKmini.padCCActionB = {|pad, vel, msg| [\B, pad, vel, msg].postln }; // change bank B pad responder (CC mode)


// Alternatively, use a custom controller spec

MPKmini.init(mpkSpec: (
keyChan: 0,
padChan: 9,
knobMap: [20,21,22,23, 24,25,26,27],
padNoteMap: [
60,61,62,63, 64,65,66,67, // Bank A
68,69,70,71, 72,73,74,75, // Bank B
],
padCCMap: [
0,1,2,3, 4,5,6,7, // Bank A
8,9,10,11, 12,13,14,15, // Bank B
],
padCCToggle: false,
xjoyMap: \bend,
yjoyMap: [31,30],
));


TODO: You can also upload a configuration to the MPK?

[control id, control type, midi channel, number]

[\keyboard, \keyboard, 0, -1], // keyboard responder, all keys on ch0
[\xy, \ccquad, 9, 10, 11, 12, 0], // xy is 4 cc numbers, ch0
[\k1, \cc, 0, 1],             // KNOBS are cc numbers ch0
[\k2, \cc, 0, 2],
[\k3, \cc, 0, 3],
[\k4, \cc, 0, 4],
[\k5, \cc, 0, 5],
[\k6, \cc, 0, 6],
[\k7, \cc, 0, 7],
[\k8, \cc, 0, 8],

// Pads are on ch1, note or cc in two banks

[\p1n_a, \note, 1, 44],          // Pads Bank A - Notes
[\p2n_a, \note, 1, 45],
[\p3n_a, \note, 1, 46],
[\p4n_a, \note, 1, 47],
[\p5n_a, \note, 1, 48],
[\p6n_a, \note, 1, 49],
[\p7n_a, \note, 1, 50],
[\p8n_a, \note, 1, 51],
[\p1cc_a, \cctoggle, 1, 1],      // Pads Bank A - Control Messages
[\p2cc_a, \cctoggle, 1, 2],
[\p3cc_a, \cctoggle, 1, 3],
[\p4cc_a, \cctoggle, 1, 4],
[\p5cc_a, \cctoggle, 1, 5],
[\p6cc_a, \cctoggle, 1, 6],
[\p7cc_a, \cctoggle, 1, 7],
[\p8cc_a, \cctoggle, 1, 8],
[\p1n_b, \note, 1, 32],          // Pads Bank B - Notes
[\p2n_b, \note, 1, 33],
[\p3n_b, \note, 1, 34],
[\p4n_b, \note, 1, 35],
[\p5n_b, \note, 1, 36],
[\p6n_b, \note, 1, 37],
[\p7n_b, \note, 1, 38],
[\p8n_b, \note, 1, 39],
[\p1cc_b, \cctoggle, 1, 9],      // Pads Bank B - Control Messages
[\p2cc_b, \cctoggle, 1, 10],
[\p3cc_b, \cctoggle, 1, 11],
[\p4cc_b, \cctoggle, 1, 12],
[\p5cc_b, \cctoggle, 1, 13],
[\p6cc_b, \cctoggle, 1, 14],
[\p7cc_b, \cctoggle, 1, 15],
[\p8cc_b, \cctoggle, 1, 16],
];
);

________________________________________________________________*/


MPKmini {
    classvar <device = nil;
    classvar <port = nil;
    classvar <initialized = false;
    classvar <keyChan, <padChan, <knobMap, <padNoteMap, <padCCMap, <padCCToggle, <xjoyMap, <yjoyMap;

    classvar <keyboardActions, <knobActions, <xyActions;
    classvar <padActions;

    classvar <midiFuncs;
    classvar <xyVal;

    classvar <knobValues;

    classvar <program=0;
    classvar <programNames, <programColors;

    classvar <win, <progText;

    *init {|verbose=false, mpkSpec=nil, mpkPreset=1|
        /*
        If mpkPreset is set and spec is nil, the setup assumes one of the MPK Factory presets 1-4
        */

        if(MIDIClient.initialized.not) {
            MIDIClient.init(verbose: verbose)
        } {
            MIDIClient.disposeClient; MIDIClient.init(verbose: verbose);
        };

        MIDIClient.sources.do {|dev, idx|
            if(dev.device == "MPK Mini Mk II") {
                device = dev;
                port = idx;
            };
        };

        if(device.isNil) {
            "MPK Mini Device Not Found, check MIDIClient.sources".error.throw;
        };

        MIDIIn.connect(inport: port, device: device);

        xyVal=[0.0,0.0];

        if(mpkSpec.notNil) {
            keyChan = mpkSpec.keyChan;
            padChan = mpkSpec.padChan;
            knobMap = mpkSpec.knobMap;
            padNoteMap = mpkSpec.padNoteMap;
            padCCMap = mpkSpec.padCCMap;
            padCCToggle = mpkSpec.padCCToggle;
            xjoyMap = mpkSpec.xjoyMap;
            yjoyMap = mpkSpec.yjoyMap;
        } {
            "Configuring MPKmini for Factory Preset %".format(mpkPreset).warn;
            switch(mpkPreset,
                1, {
                    keyChan=0;
                    padChan=0;
                    knobMap=[1,2,3,4,5,6,7,8];
                    padNoteMap=[
                        44,45,46,47,48,49,50,51, // Bank A
                        32,33,34,35,36,37,38,39, // Bank B
                    ];
                    padCCMap=[
                        20,21,22,23,24,25,26,27, // Bank A
                        28,29,30,31,32,33,34,35, // Bank B
                    ];
                    padCCToggle=true;
                    xjoyMap = \bend;
                    yjoyMap = [1]; // this is really weird! there's no up/down
                },
                2, {
                    keyChan=0;
                    padChan=9;
                    knobMap=[20,21,22,23,16,17,18,19];

                    padNoteMap=[
                        37,36,42,82,48,38,46,44, // Bank A
                        48,47,45,43,49,55,51,53, // Bank B
                    ];
                    padCCMap=[
                        1,2,3,4,5,6,7,8, // Bank A
                        9,10,11,12,13,14,15,16, // Bank B
                    ];
                    padCCToggle=true;

                    xjoyMap = \bend;
                    yjoyMap = [1];

                },
                3, {
                    keyChan=0;
                    padChan=9;
                    knobMap=[20,21,22,23, 24,25,26,27];

                    padNoteMap=[
                        48,50,52,53, 55,57,59,60, // Bank A
                        60,62,64,65, 67,69,71,72, // Bank B
                    ];
                    padCCMap=[
                        0,1,2,3, 4,5,6,7, // Bank A
                        8,9,10,11, 12,13,14,15, // Bank B
                    ];
                    padCCToggle=true;

                    xjoyMap = \bend;
                    yjoyMap = [1];

                },
                4, {
                    keyChan=0;
                    padChan=9;
                    knobMap=[20,21,22,23, 24,25,26,27];

                    padNoteMap=[
                        60,61,62,63, 64,65,66,67, // Bank A
                        68,69,70,71, 72,73,74,75, // Bank B
                    ];
                    padCCMap=[
                        0,1,2,3, 4,5,6,7, // Bank A
                        8,9,10,11, 12,13,14,15, // Bank B
                    ];
                    padCCToggle=false;

                    xjoyMap = \bend;
                    yjoyMap = [31,30];

                },
                { "Cannot initialize MIDIFuncs! Unknown mpk preset '%' and no mpkSpec set!".format(mpkPreset).error.throw }
            );
        };

        // Initialize program names and colors
        programNames = 8.collect(_.asString);
        programColors = [
            Color.black,
            Color.blue,
            Color.new(0.2, 0.7, 1),
            Color.red,
            Color.new(1, 0.7, 0.5),
            Color.grey,
            Color.new(0.5, 0.5, 1),
            Color.magenta
        ];
        knobValues = 8.collect {|it|
            Array.fill(8, {0});
        };

        // Set up default midi callbacks
        keyboardActions = 8.collect({|it|
            {|key, vel, msg| "key %: % %".format(key,msg,vel).postln }
        });
        knobActions = 8.collect({|it|
            {|knob, val|
                "k%: %".format(knob, val).postln;
            }
        });
        xyActions = 8.collect({|it|
            {|xpos, ypos| "xy: % %".format(xpos, ypos).postln }
        });

        // pad callbacks by Bank and Note/CC mode
        padActions = 8.collect({|it|
            var padsets = Dictionary.new;
            padsets.put(\An, {|pad,vel,msg| "p%: Bank A note %, %".format(pad, vel, msg).postln });
            padsets.put(\Acc, {|pad,vel,msg| "p%: Bank A cc %, %".format(pad, vel, msg).postln });
            padsets.put(\Bn, {|pad,vel,msg| "p%: Bank B note %, %".format(pad, vel, msg).postln });
            padsets.put(\Bcc, {|pad,vel,msg| "p%: Bank B cc %, %".format(pad, vel, msg).postln });
            padsets;
        });

        // Initialize MIDI responder functions (MIDIFunc)
        this.initMidiFuncs;

        initialized = true;
    }




    // By default CMD-PERIOD will erase all MIDIFuncs.
    // Pretty annoying!
    // This function helps recover from that.
    *initMidiFuncs {
        if(midiFuncs.notNil) {
            midiFuncs.do {|fnc| fnc.free };
        };
        midiFuncs = Dictionary.new;

        midiFuncs.put(\progChange, MIDIFunc({|val,num|
            this.program_(val);
        }, nil, padChan, \program, device.uid));

        midiFuncs.put(\keyboardNoteOn, MIDIFunc({|val,num|
            keyboardActions[program].value(num, val, \noteOn);
        }, nil, keyChan, \noteOn, device.uid));

        midiFuncs.put(\keyboardNoteOff, MIDIFunc({|val,num|
            keyboardActions[program].value(num, val, \noteOff);
        }, nil, keyChan, \noteOff, device.uid));

        midiFuncs.put(\knobs, MIDIFunc({|val,num|
            // Map cc num to knob number
            num = knobMap.indexOf(num);
            knobValues[program][num] = val;
            knobActions[program].value(num+1, val);
        }, knobMap, keyChan, \control, device.uid));

        midiFuncs.put(\padsOn, MIDIFunc({|val,num|
            // Map note num to pad number
            num = padNoteMap.indexOf(num);
            if(num < 8) { // Bank A
                padActions[program].at(\An).value(num+1, val, \noteOn);
            } { // Bank B
                num = num - 8;
                padActions[program].at(\Bn).value(num+1, val, \noteOn);
            };
        }, padNoteMap, padChan, \noteOn, device.uid));

        midiFuncs.put(\padsOff, MIDIFunc({|val,num|
            // Map note num to pad number
            num = padNoteMap.indexOf(num);
            if(num < 8) { // Bank A
                padActions[program].at(\An).value(num+1, val, \noteOff);
            } { // Bank B
                num = num - 8;
                padActions[program].at(\Bn).value(num+1, val, \noteOff);
            };
        }, padNoteMap, padChan, \noteOff, device.uid));

        midiFuncs.put(\padsCC, MIDIFunc({|val,num|
            var toggleVal;
            num = padCCMap.indexOf(num); // Map note num to pad number
            if(padCCToggle == true) { // toggle behavior
                if(val == 0) { toggleVal = \off } { toggleVal = \on };
            } {
                toggleVal = \none;
            };

            if(num < 8) { // Bank A
                padActions[program].at(\Acc).value(num+1, val, toggleVal);
            } { // Bank B
                num = num - 8;
                padActions[program].at(\Bcc).value(num+1, val, toggleVal);
            };
        }, padCCMap, padChan, \control, device.uid));

        if(xjoyMap == \bend) { // joystick pitchbend
            midiFuncs.put(\xjoy, MIDIFunc({|val|
                // Bend value is 0 - 8192 - 16383
                xyVal[0] = val.linlin(0, 16383, -1.0, 1.0).round(0.001);
                xyActions[program].value(xyVal[0], xyVal[1]);
            }, nil, keyChan, \bend, device.uid ));
        } { // joystick cc
            midiFuncs.put(\xjoy, MIDIFunc({|val,num|
                num = xjoyMap.indexOf(num); // Map cc num to joystick direction
                switch(num,
                    0, { xyVal[0] = val.linlin(0, 127, 0, -1.0) }, // left
                    1, { xyVal[0] = val.linlin(0, 127, 0, 1.0) }, // right
                );
                xyActions[program].value(xyVal[0], xyVal[1]);
            }, xjoyMap, keyChan, \control, device.uid));
        };

        if(yjoyMap == \bend) { // joystick pitchbend
            midiFuncs.put(\yjoy, MIDIFunc({|val|
                // Bend value is 0 - 8192 - 16383
                xyVal[1] = val.linlin(0, 16383, -1.0, 1.0).round(0.001);
                xyActions[program].value(xyVal[0], xyVal[1]);
            }, nil, keyChan, \bend, device.uid ));
        } { // joystick cc
            midiFuncs.put(\yjoy, MIDIFunc({|val,num|
                num = yjoyMap.indexOf(num); // Map cc num to joystick direction
                switch(num,
                    0, { xyVal[1] = val.linlin(0, 127, 0, 1.0) }, // up
                    1, { xyVal[1] = val.linlin(0, 127, 0, -1.0) }, // down
                );
                xyActions[program].value(xyVal[0], xyVal[1]);
            }, yjoyMap, keyChan, \control, device.uid));
        };

    }

    *program_ {|prog|
        program = prog;
        "Program Change to %".format(programNames[prog]).warn;
        if(progText.notNil) {

            {
                progText.string_((program+1).asString);
                4.do {
                    progText.background_(Color.rand);
                    0.05.wait;
                };
                progText.background_(programColors[prog]);
            }.fork(AppClock);

        };
    }

    *knobValue {|knob|
        ^knobValues[program][knob];
    }


    // Keyboard responder {|note,vel,msg|}
    *keyAction_ {|callback, prog=0|
        keyboardActions[prog] = callback;
    }

    // XY joystick responder {|xpos,ypos|}
    *xyAction_ {|callback, prog=0|
        xyActions[prog] = callback;
    }

    // Knob responder {|knob, val|}
    *knobAction_ {|callback, prog=0|
        knobActions[prog] = callback;
    }

    // Pad responders
    *padNoteActionA_ {|callback, prog=0|
        padActions[prog].put(\An, callback);
    }
    *padNoteActionB_ {|callback, prog=0|
        padActions[prog].put(\Bn, callback);
    }
    *padCCActionA_ {|callback, prog=0|
        padActions[prog].put(\Acc, callback);
    }
    *padCCActionB_ {|callback, prog=0|
        padActions[prog].put(\Bcc, callback);
    }

    // Pad responders, padBank is \An or \Bn / \Acc or \Bcc
    // {|pad,vel,msg|}
    *padAction_ {|padBank, callback, prog=0|
        padActions[prog].put(padBank, callback);
    }

    *gui {|position|
        var win, top=0, left=0, width=100, height=100;
        var styler, childView;
        top = Window.screenBounds.height - height;
        left = Window.screenBounds.width - left;
        if(win.notNil) {
            if(win.isClosed.not) {
                win.front;
                ^win;
            }
        };

        if(position.notNil) {
            top=position.y; left=position.x;
        };

        win = Window("MPKmini", Rect(left,top,width,height));
        styler = GUIStyler(win);

        childView = styler.getView("MPK", win.view.bounds, gap: 10@10);

        styler.getSizableText(childView, "program", 100);
        progText = styler.getSizableText(childView, (program+1), 100, fontSize: 64, bold: true);

        ^win.alwaysOnTop_(true).front;

    }

}
