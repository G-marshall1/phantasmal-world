# newserv game data tables

These JSON tables are from [newserv](https://github.com/fuzziqersoftware/newserv)
(Copyright (c) Martin Michelsen, MIT License -- see LICENSE-newserv), which ships them as
faithful conversions of the game's own data files. They are the source of truth for this
project's generated stat tables; see :web:assets-generation for the converters that consume
them.

Format notes: JSON with `//` comments and hex integer literals (`0x28`) -- preprocess before
strict parsing.
