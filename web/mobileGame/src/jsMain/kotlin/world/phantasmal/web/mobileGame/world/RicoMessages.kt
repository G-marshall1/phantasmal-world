package world.phantasmal.web.mobileGame.world

/**
 * Red Ring Rico's message pod logs, keyed by the client message id each pod placement carries
 * (paramsI[2] on object type 141 -- the same id space the real client resolves against its
 * unitxt table).
 *
 * The texts are the game's own, transcribed verbatim from the script (pscave.com's Episode 1
 * transcription). The *grouping* of lines into pods and the id assignment are reconstructed:
 * ids ascend with the story and each id's text is drawn from the correct area's sequence, but
 * the client's exact id-to-text table ships only inside Sega's unitxt, which has no
 * copyright-free dump. If a specific pod ever reads out of order on the ground, its lines
 * belong a slot over -- the words themselves are always Rico's.
 */
val RICO_MESSAGES: Map<Int, String> = mapOf(
    // ---- Forest 1 ----
    201 to "Ah, testing, testing...\nCough!\nI'm Rico, Rico Tyrell. I'm a hunter.\n" +
        "This capsule is for anyone who has come here looking for me. I hope this helps you.",
    202 to "I don't know who you are, but you must know that there's something unusual " +
        "about Ragol.\nThis is important. Pay attention to everything around you if you " +
        "want to survive.",
    203 to "Boomas... I don't like their weird faces.\n" +
        "Be careful, you don't want to get surrounded by them.",
    205 to "Red gates are locked. Green means they're open. Of course you knew that...\n" +
        "Don't show your back to a Savage Wolf! It'll attack if it sees this opening.",
    206 to "I know I'm a fool. This won't make me any richer.\n" +
        "Perhaps that's why I'm exalted by them... Red Ring Rico, ha, ha.\n" +
        "But I'm not really the great hunter citizens say I am.\n" +
        "They needed a hero. And I just happened to fill that position.",
    207 to "Mothmants will keep appearing, one after another.\n" +
        "You have to strike them out at the root.",
    208 to "Disable the laser fences by using the colored switches.",
    214 to "Rag Rappies are cowards, but they like to attack straight on.\n" +
        "When a Rag Rappy falls down, it may still be alive. So be careful.",
    216 to "What made the animals become so violent all of a sudden? They weren't before.\n" +
        "They were very quiet and friendly...\nThere must be some cause. I'll find it.",

    // ---- Forest 2 ----
    204 to "Wow... bodies of dead animals...\nWe hunters sometimes use firearms, but this...\n" +
        "They were killed by firearms that are much stronger than ours!",
    209 to "A disaster occurred. Things were shaking, then something broke through the " +
        "surface.\nAnd then it exploded in the Central Dome!\nI don't know what to say...",
    210 to "For 7 years, we've tried to adjust and improve the environment.\n" +
        "What was it? Was it related to the accidents we've had recently?",
    212 to "Look for a switch to activate the bridge!",
    213 to "Pioneer 1 may have damaged the ecological system of Ragol before we were aware " +
        "of it.\nSo, the native creatures tried to remove the invaders. That's one " +
        "supposition.\nBut what about the explosion!?\n" +
        "I need more information. I have to go do some research.",
    218 to "If you can't hit a Hildebear, try standing in front of it.\n" +
        "Of course, I don't miss.",
    219 to "I heard that this tall column was built to commemorate the immigration of " +
        "Pioneer 1.\nBut... it may just be me, but it doesn't look very new.\n" +
        "And these patterns... aren't they characters?",

    // ---- Cave 1 ----
    220 to "Wow... this cave is a treasure trove of discoveries.\n" +
        "Creatures that have never been seen by people. Completely unknown animals.\n" +
        "They look like mutant forms of the native animals...",
    223 to "Perhaps the government has kept this a secret...?\n" +
        "It's possible... but for what purpose?",
    224 to "Be careful of the Poison Lily's toxic spit.",
    225 to "If you hear an alert, be careful! There must be land mines around you.\n" +
        "You can detect them if you use the Trap Vision.",
    226 to "The Nano Dragon is a fierce monster. It becomes stronger as it kills others.\n" +
        "I suggest you defeat it as quickly as possible.",
    239 to "Evil Sharks attack in obvious straight movement patterns. Don't get surrounded.",

    // ---- Cave 2 ----
    221 to "The attack range of the Grass Assassin is wide.\n" +
        "Move quickly to enter its blind spot!",
    222 to "Pofuilly Slime tends to attack at close range.\n" +
        "That's not good for Rangers and Forces. Be careful.",
    227 to "You may find yourself trapped by a parasite mine in the ground.\n" +
        "To free yourself, ask someone to hit you with a technique or a gun.",
    229 to "I know that Pioneer 1 had some strange aspects to it.\n" +
        "In the data that I procured, the consumption rate was much higher than the " +
        "population...\nPerhaps there were a lot of non-registered citizens onboard.\n" +
        "Why? What was their purpose?",
    237 to "I never imagined that a trap would look like a switch!\nHow devious!",
    262 to "My first question about Ragol was, \"Why didn't any sentient life exist " +
        "here?\"\nBut... look at this monument! This is identical to the one I saw in the " +
        "forest!\nIt's NOT ours though... Was there an ancient civilization on Ragol?\n" +
        "But these monuments are the only evidence I see...\n" +
        "It'd be strange if there was a civilization, indeed...\n" +
        "Can I decipher the characters with my simple tools?",

    // ---- Cave 3 ----
    228 to "Ah, the characters on the monument... I don't have any idea how to proceed.\n" +
        "I need more samples.",
    231 to "Pan Arms are a little tricky.\nThey get stronger when they combine.\n" +
        "But they're really quick when separated. Don't panic, OK?",
    232 to "I've got such mixed emotions... I'm scared, but excited, inspired even!\n" +
        "Should I act as a scientist now?\n" +
        "Or should I act as a hunter who is facing unknown enemies?\n" +
        "I feel like someone's herding me somewhere... but where?\n" +
        "To the underground... I feel like I'm being invited.\n" +
        "I saw it with my own eyes.\n" +
        "An animal metamorphosed when it was pierced by a tentacle from that giant worm.\n" +
        "Were the monsters I saw in the cave all changed by that giant worm?",

    // ---- Mine 1 ----
    250 to "This area... Apparently technology was involved in creating this.\n" +
        "Why did they dig so deeply into the ground?",
    251 to "I'm attacked by robots this time... What are they?!\n" +
        "They were customized robots originally for industrial use. ...Who did this?\n" +
        "I can understand animals being metamorphosed by a crustacean into mutants, but...\n" +
        "These were robots. Somebody modified them. Was it done by someone from Pioneer 1?",
    252 to "Don't panic if you see more than one Sinow Beat. Just find the real body.",
    255 to "To defeat Canadines, first disrupt their formation.\n" +
        "The red one is the leader. Kill it first. It makes the battle easier.",
    256 to "A Dubchic will get up when knocked down from an attack.\n" +
        "There must be a switch to kill them all simultaneously. Look for it.",

    // ---- Mine 2 ----
    253 to "Here, I found the third one. Will it fit together when all the parts are " +
        "combined?\n\"Light, darkness, pair, exist, unlimited, rule, seal...\"\n" +
        "I can make out each word, but I still don't understand the meaning of the whole " +
        "thing.",
    254 to "To defeat a Garanz, you have to strip it of its hard shell first.\n" +
        "Its protection will drop, but its attack power will be enhanced. Be careful!",
    258 to "I heard a rumor that the government was building a secret underground factory.\n" +
        "Were the robots manufactured in that secret factory?\n" +
        "Or, was the factory a decoy?\n" +
        "The government was developing another project behind it...\n" +
        "Information is always controlled by the government.\n" +
        "We don't know the truth at all.\n" +
        "We hunters are always used by the government. We're just tools to them.",

    // ---- Ruins 1 (and the descent between Mine and Ruins) ----
    270 to "I can now say that there was an ancient civilization on this planet.\n" +
        "Ruins buried in the ground. This is the evidence.\n" +
        "The government was about to secretly conduct an excavation to study the ruins.",
    271 to "No intelligent life was discovered when Pioneer 1 landed here...\n" +
        "Something else must have caused the destruction of this ancient civilization.\n" +
        "What happened on Ragol in the past?",
    272 to "The government was decoding the characters as well.\n" +
        "Here is their analysis. I'll try to fill in the gaps with my own data.\n" +
        "\"Light, darkness,... a pair, no, ... exist, no exist...\n" +
        "unlimited, seal, ... MUUT DITTS POUMN\"...?\n" +
        "What's the last line? An incantation?",
    273 to "Seal, seal... What is sealed? Where?\n" +
        "Is it about this door? Was it sealed with the words, MUUT DITTS POUMN?\n" +
        "Maybe each word in the incantation represents something?\n" +
        "I found three monuments... Are they keys to open the door?",
    276 to "Why am I here, with monsters lurking everywhere?\n" +
        "Did the army even stand a chance against them?",
    277 to "Wreckage...! These weapons are from Pioneer 1.\n" +
        "They must have already entered the ruins, and from the look of things, fighting " +
        "went on.\nIt must've been a big battle. Seems our army was hurt badly.",
    278 to "I can't move... what a horrific trap.\nI wish I had a friend here...",
    279 to "What is this big hole?\n" +
        "It looks like...remnants of some type of energy explosion. ...Energy...?\n" +
        "Was the Central Dome destroyed by this!?",
    289 to "Frontal attacks against Delsabers are ineffective. So don't try it.\n" +
        "Go around to its back.",
    291 to "I can see a distant view through this window...\n" +
        "The \"ruins\" are huge. I would never have imagined such a great civilization...",
    292 to "Claws should be easy to defeat. They are weak.\n" +
        "But be careful when they combine into a Bulclaw.",

    // ---- Ruins 2 ----
    274 to "Do you see the crystals floating around a Chaos Sorcerer?\n" +
        "One is used for attack, and the other is for healing.",
    275 to "Dark Belra's are very slow, but they have lots of health.\n" +
        "Prepare for a long battle.",
    280 to "Don't let yourself get surrounded by Dimenians!",
    281 to "Those strange characters were found here and there.\n" +
        "I think I have enough samples to decipher the meaning of the message on the " +
        "monument.\n\"Light makes darkness, a pair exists, but it doesn't always exist.\n" +
        "Reincarnation goes forever. The rule is here.\n" +
        "It should be sealed. MUUT DITTS POUMN\"...\nDoes it make sense?\n" +
        "I wish I had enough time to study these unknown characters...",
    282 to "I haven't studied all the characters yet, but I've got some useful information.\n" +
        "This is the most important fact that I have found.\n" +
        "There was NO ancient civilization on Ragol.\nWe didn't discover ruins.\n" +
        "This is a spaceship. A gigantic spaceship.\nI'm now inside the ancient spaceship.",
    293 to "Well, it's not just a spaceship.\nIt's a... \"casket.\"\n" +
        "Something or somebody was sealed in this spaceship to remain buried here.\n" +
        "What was IT? Why was something buried in such a manner?\n" +
        "Anyway, I know that a monster is sleeping in this cave.\n" +
        "We've opened the forbidden door.",

    // ---- Ruins 3 ----
    283 to "You need to wait until a Dark Gunner stops to hit it.\n" +
        "It's impervious to damage when it's moving around.",
    284 to "Still moving...\nThis ship's still operating!",
    285 to "Be careful of the Chaos Bringer's charge.\n" +
        "Dodge the charge, then attack it from behind.",
    286 to "I want to run away!\nBut I have no place to return to...\n" +
        "Perhaps no one will ever find this message and listen to it. Ever...\n" +
        "Pioneer 2 will not come down when they discover that this planet is dangerous.\n" +
        "Will somebody from Pioneer 2 still come to save us? Who knows?\n" +
        "Regardless, I leave this message here. This is evidence of my existence.",
    287 to "Dark Falz! That's the name.\n" +
        "The god of destruction that revives in the millennial cycle.\n" +
        "Perhaps this entity encountered a civilization thousands of years ago.\n" +
        "They could not defeat it, but managed to seal it in this gigantic spaceship.\n" +
        "They abandoned it somewhere far off their planet.\nIt was this place, Ragol.\n" +
        "We've come to a terrible place at the worst possible time...",
    290 to "This mist is very unusual... Be careful!\n" +
        "Dark Falz is a consciousness. This entity has no body.\n" +
        "I miss my father.\nI wasn't a very good daughter... was I? Is my father OK now?\n" +
        "Don't let it come in.\nThe dark consciousness looks for the best animal\n" +
        "to obtain its temporal host body.\nThe door is already opened.\nWe opened it.\n" +
        "This may be the beginning of the end of the universe.\n" +
        "We have to do something...\nWe must do it. We have to defeat it now!",
)
