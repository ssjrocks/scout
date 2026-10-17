package com.ssjrocks.scout.data

import java.net.URLEncoder

/**
 * Built-in topics and sources. The selection comes from the owner's browsing history
 * (Oct 2025 - Oct 2026): PC hardware (ROG Z890, RTX 5080), local AI and Claude Code, Ubuntu and
 * self-hosting (Immich, Jellyfin, RomM), ESP32/Meshtastic, Bambu/Ender 3D printing, OpenFront and
 * Steam games, retro consoles, Game of Thrones, science, military history, surveillance and
 * privacy, internet investigations, stand-up comedy, OzBargain-style shopping, e-bikes, transport
 * work, and local news for Logan/Brisbane. Sources marked enabled = false are suggestions.
 */
object Catalog {

    val topics = listOf(
        Topic("local", "Logan & Brisbane", "📍", listOf(
            "logan", "brisbane", "ipswich", "gold coast", "park ridge", "beenleigh", "springwood", "slacks creek",
            "jimboomba", "browns plains", "redland", "moreton bay", "queensland", "qld", "south east queensland", "seq",
            "crestmead", "marsden", "loganlea", "kingston", "woodridge", "yarrabilba", "flagstone", "logan city",
            "logan hospital", "translink",
        )),
        Topic("australia", "Australia", "🇦🇺", listOf(
            "australia", "australian", "australians", "albanese", "canberra", "sydney", "melbourne", "perth", "adelaide",
            "nsw", "centrelink", "ato", "medicare", "ndis", "aussie", "anz", "commonwealth bank", "woolworths", "coles",
        )),
        Topic("world", "World", "🌍", emptyList()),
        Topic("tech", "Tech", "💡", listOf(
            "apple", "google", "microsoft", "samsung", "android", "iphone", "meta", "smartphone", "galaxy s26", "pixel",
            "windows 11", "windows 12", "chrome", "firefox", "starlink", "app store", "big tech",
        )),
        Topic("pc", "PC hardware", "🖥️", listOf(
            "nvidia", "amd", "intel", "rtx", "radeon", "geforce", "gpu", "gpus", "cpu", "cpus", "ryzen", "core ultra",
            "arrow lake", "panther lake", "z890", "motherboard", "motherboards", "asus", "rog", "msi", "gigabyte", "ddr5",
            "ssd", "nvme", "dlss", "fsr", "overclock", "overclocking", "5080", "5090", "5070", "nzxt", "corsair", "oled monitor",
            "ram prices", "memory prices", "micron", "pcie", "graphics card", "pc build",
        )),
        Topic("ai", "AI & LLMs", "🤖", listOf(
            "ai", "a.i.", "llm", "llms", "openai", "chatgpt", "gpt", "claude", "anthropic", "gemini", "deepmind", "llama",
            "qwen", "mistral", "deepseek", "ollama", "hugging face", "ai agent", "ai agents", "copilot", "codex",
            "stable diffusion", "comfyui", "flux", "sora", "midjourney", "machine learning", "neural network", "chatbot",
            "grok", "xai", "artificial intelligence", "vibe coding",
        )),
        Topic("linux", "Linux & self-hosting", "🐧", listOf(
            "linux", "ubuntu", "debian", "fedora", "arch linux", "kde", "gnome", "wayland", "kernel", "steam deck",
            "proton", "docker", "self-hosted", "selfhosted", "self-hosting", "homelab", "nas", "immich", "jellyfin", "plex",
            "nextcloud", "tailscale", "proxmox", "open source", "open-source", "foss", "lineageos", "grapheneos",
        )),
        Topic("smarthome", "Smart home", "🏠", listOf(
            "home assistant", "esphome", "zigbee", "matter", "smart home", "frigate", "tasmota", "mqtt", "node-red",
        )),
        Topic("maker", "Electronics & makers", "🔌", listOf(
            "esp32", "arduino", "raspberry pi", "pi 5", "microcontroller", "pcb", "solder", "soldering", "meshtastic",
            "lora", "sdr", "ham radio", "rp2040", "rp2350", "waveshare", "lilygo", "oscilloscope", "kicad", "diy",
            "smart glasses", "e-ink", "teardown",
        )),
        Topic("printing", "3D printing", "🧱", listOf(
            "3d print", "3d printer", "3d printers", "3d printing", "3d-printed", "3d printed", "bambu", "prusa",
            "creality", "ender", "filament", "resin printer", "elegoo", "anycubic", "makerworld", "printables",
            "orcaslicer", "voron",
        )),
        Topic("gaming", "Gaming", "🎮", listOf(
            "gaming", "gamers", "steam", "playstation", "ps5", "xbox", "nintendo", "switch 2", "valve", "epic games",
            "gta", "gta 6", "rockstar", "ubisoft", "bethesda", "fallout", "elder scrolls", "satisfactory",
            "7 days to die", "no man's sky", "schedule i", "openfront", "minecraft", "early access", "dlc",
            "patch notes", "video game", "video games", "star citizen",
        )),
        Topic("retro", "Retro gaming", "👾", listOf(
            "retro", "emulator", "emulators", "emulation", "nes", "snes", "sega", "mega drive", "famicom", "game boy",
            "gba", "n64", "ps1", "ps2", "dreamcast", "arcade", "mame", "retroarch", "analogue", "crt", "mister fpga", "romm",
        )),
        Topic("got", "Game of Thrones", "🐉", listOf(
            "game of thrones", "house of the dragon", "westeros", "targaryen", "targaryens", "stark", "lannister",
            "george r.r. martin", "george rr martin", "grrm", "winds of winter", "knight of the seven kingdoms",
            "asoiaf", "dunk and egg", "daenerys", "jon snow", "iron throne",
        )),
        Topic("science", "Science & space", "🔭", listOf(
            "science", "scientists", "nasa", "space", "spacex", "rocket", "mars", "moon", "planet", "exoplanet",
            "galaxy", "telescope", "jwst", "webb", "physics", "physicists", "quantum", "asteroid", "comet",
            "black hole", "fossil", "dinosaur", "evolution", "species", "astronomers", "universe",
        )),
        Topic("military", "Military & history", "🎖️", listOf(
            "military", "army", "navy", "air force", "raaf", "adf", "fighter jet", "f-35", "f-22", "drone", "drones",
            "tank", "tanks", "missile", "missiles", "ww1", "ww2", "world war", "historian", "historians", "ancient",
            "archaeologists", "archaeology", "pentagon", "defence", "defense", "warship", "submarine", "aukus",
            "wild west",
        )),
        Topic("security", "Privacy & security", "🔒", listOf(
            "privacy", "surveillance", "security", "hack", "hacker", "hackers", "hacked", "breach", "ransomware",
            "malware", "vulnerability", "exploit", "cve", "phishing", "scam", "scams", "scammer", "encryption", "vpn",
            "tracking", "alpr", "anpr", "flock", "facial recognition", "spyware", "zero-day", "data breach", "cyber",
            "right to repair",
        )),
        Topic("internet", "Internet culture", "📱", listOf(
            "meme", "memes", "viral", "youtuber", "youtubers", "streamer", "tiktok", "twitch", "influencer", "drama",
            "coffeezilla", "mrbeast", "know your meme",
        )),
        Topic("comedy", "Comedy", "😂", listOf(
            "comedy", "comedian", "comedians", "stand-up", "standup", "taskmaster", "comedy special", "sketch",
        )),
        Topic("screen", "TV & film", "🎬", listOf(
            "movie", "movies", "film", "trailer", "series", "season", "episode", "netflix", "hbo", "disney+",
            "prime video", "box office", "actor", "actress", "sequel", "marvel", "star wars", "reacher", "the rookie",
            "tulsa king", "breaking bad", "house m.d.",
        )),
        Topic("sport", "Sport", "🏉", listOf(
            "afl", "aflw", "nrl", "nrlw", "rugby", "rugby league", "rugby union", "cricket", "soccer", "a-league",
            "tennis", "golf", "olympics", "olympic", "grand prix", "f1", "formula 1", "motogp", "ufc", "nba", "nfl",
            "mlb", "matildas", "socceroos", "wallabies", "kangaroos", "broncos", "titans", "dolphins", "cowboys",
            "crows", "suns", "lions", "state of origin", "grand final", "premiership", "ashes", "wimbledon",
            "world cup", "test match", "melbourne cup", "supercars",
        )),
        Topic("deals", "Deals", "🏷️", emptyList()),
        Topic("ev", "EVs & e-bikes", "⚡", listOf(
            "ev", "evs", "electric vehicle", "electric vehicles", "tesla", "byd", "e-bike", "ebike", "e-bikes", "ebikes",
            "electric motorbike", "electric motorcycle", "ev charging",
        )),
        Topic("transport", "Transport & logistics", "🚛", listOf(
            "truck", "trucks", "trucking", "truckie", "truckies", "freight", "logistics", "warehouse", "forklift",
            "supply chain", "heavy vehicle", "prime mover", "road freight",
        )),
    )

    val topicById = topics.associateBy { it.id }

    /** Nothing in the browsing history points at sport, so it starts ranked down (not hidden). */
    val UNFOLLOWED_BY_DEFAULT = setOf("sport")

    private fun news(id: String, name: String, url: String, site: String, topic: String, enabled: Boolean = true, weight: Float = 1f, include: String = "") =
        Source(id, name, SourceType.NEWS, url, topic, site, enabled, weight, include)

    private fun reddit(sub: String, topic: String, enabled: Boolean = true, weight: Float = 1f) =
        Source("r_" + sub.lowercase(), "r/$sub", SourceType.REDDIT, redditUrl(sub), topic, "reddit.com", enabled, weight)

    private fun yt(id: String, name: String, channelId: String, topic: String, enabled: Boolean = true, weight: Float = 1f) =
        Source("yt_$id", name, SourceType.YOUTUBE, youtubeUrl(channelId), topic, "youtube.com", enabled, weight)

    private fun gh(id: String, name: String, repo: String, topic: String, enabled: Boolean = true) =
        Source("gh_$id", name, SourceType.RELEASES, "https://github.com/$repo/releases.atom", topic, "github.com", enabled, 0.8f)

    private fun steam(id: String, name: String, appId: Int, topic: String, enabled: Boolean = true) =
        Source("steam_$id", name, SourceType.STEAM, "https://store.steampowered.com/feeds/news/app/$appId/", topic, "steampowered.com", enabled, 0.8f)

    private fun gnews(id: String, name: String, query: String, topic: String, enabled: Boolean = true, weight: Float = 1f) =
        Source("gn_$id", name, SourceType.GNEWS, googleNewsUrl(query), topic, "news.google.com", enabled, weight)

    fun redditUrl(sub: String) = "https://www.reddit.com/r/$sub/top/.rss?t=day&limit=15"
    fun youtubeUrl(channelId: String) = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
    fun googleNewsUrl(query: String) =
        "https://news.google.com/rss/search?q=" + URLEncoder.encode(query, "UTF-8") + "&hl=en-AU&gl=AU&ceid=AU:en"

    val sources: List<Source> = listOf(
        // Logan & Brisbane
        news("bt_qld", "Brisbane Times", "https://www.brisbanetimes.com.au/rss/national/queensland.xml", "brisbanetimes.com.au", "local"),
        news("7news_qld", "7NEWS Queensland", "https://7news.com.au/news/qld/feed", "7news.com.au", "local"),
        news("qps_logan", "Queensland Police · Logan", "https://mypolice.qld.gov.au/logan/feed/", "mypolice.qld.gov.au", "local", weight = 0.7f),
        gnews("logan", "Google News · Logan", "Logan Queensland when:3d", "local"),
        gnews("brisbane", "Google News · Brisbane", "Brisbane when:2d", "local", weight = 0.8f),
        // Australia
        news("abc_top", "ABC News", "https://www.abc.net.au/news/feed/45910/rss.xml", "abc.net.au", "australia"),
        news("abc_justin", "ABC News · Just In", "https://www.abc.net.au/news/feed/51120/rss.xml", "abc.net.au", "australia", enabled = false),
        news("guardian_au", "Guardian Australia", "https://www.theguardian.com/australia-news/rss", "theguardian.com", "australia"),
        news("sbs", "SBS News", "https://www.sbs.com.au/news/feed", "sbs.com.au", "australia", weight = 0.8f),
        // World
        news("bbc_world", "BBC News · World", "https://feeds.bbci.co.uk/news/world/rss.xml", "bbc.co.uk", "world"),
        news("guardian_world", "Guardian · World", "https://www.theguardian.com/world/rss", "theguardian.com", "world", enabled = false),
        // Tech
        news("verge", "The Verge", "https://www.theverge.com/rss/index.xml", "theverge.com", "tech"),
        news("ars", "Ars Technica", "https://feeds.arstechnica.com/arstechnica/index", "arstechnica.com", "tech"),
        Source("hn", "Hacker News", SourceType.HN, "https://hnrss.org/frontpage?points=150", "tech", "news.ycombinator.com"),
        // PC hardware
        news("toms", "Tom's Hardware", "https://www.tomshardware.com/feeds.xml", "tomshardware.com", "pc"),
        news("tpu", "TechPowerUp", "https://www.techpowerup.com/rss/news", "techpowerup.com", "pc"),
        news("wccftech", "Wccftech", "https://wccftech.com/feed/", "wccftech.com", "pc", weight = 0.6f),
        yt("gn", "Gamers Nexus", "UChIs72whgZI9w6d6FhwGGHA", "pc"),
        yt("ltt", "Linus Tech Tips", "UCXuqSBlHAE6Xw-yeJA0Tunw", "pc"),
        yt("rossmann", "Louis Rossmann", "UCl2mFZoRqjw_ELax4Yisf6w", "pc"),
        yt("zach", "Zach's Tech Turf", "UCBS1NUWY7oC6FFHaXQy4zAA", "pc"),
        yt("hub", "Hardware Unboxed", "UCI8iQa1hv7oV_Z8D35vVuSg", "pc", enabled = false),
        yt("der8auer", "der8auer", "UCGsaijjOJshS2_ZmMNZgS-g", "pc", enabled = false),
        reddit("ASUSROG", "pc"),
        reddit("hardware", "pc"),
        reddit("nvidia", "pc", enabled = false),
        // AI
        reddit("LocalLLaMA", "ai"),
        reddit("ClaudeAI", "ai"),
        reddit("ClaudeCode", "ai"),
        news("simonw", "Simon Willison", "https://simonwillison.net/atom/everything/", "simonwillison.net", "ai", weight = 0.8f),
        news("decoder", "The Decoder", "https://the-decoder.com/feed/", "the-decoder.com", "ai"),
        gh("claudecode", "Claude Code releases", "anthropics/claude-code", "ai"),
        gh("ollama", "Ollama releases", "ollama/ollama", "ai"),
        yt("codebullet", "Code Bullet", "UC0e3QhIYukixgh5VVpKHH9Q", "ai"),
        news("hfblog", "Hugging Face blog", "https://huggingface.co/blog/feed.xml", "huggingface.co", "ai", enabled = false),
        // Linux & self-hosting
        news("phoronix", "Phoronix", "https://www.phoronix.com/rss.php", "phoronix.com", "linux"),
        news("omgubuntu", "OMG! Ubuntu", "https://feeds.feedburner.com/d0od", "omgubuntu.co.uk", "linux"),
        news("9to5linux", "9to5Linux", "https://9to5linux.com/feed", "9to5linux.com", "linux"),
        news("gol", "GamingOnLinux", "https://www.gamingonlinux.com/article_rss.php", "gamingonlinux.com", "linux", weight = 0.8f),
        news("selfhst", "selfh.st weekly", "https://selfh.st/rss/", "selfh.st", "linux"),
        reddit("selfhosted", "linux"),
        gh("immich", "Immich releases", "immich-app/immich", "linux"),
        yt("bread", "Bread on Penguins", "UCwHwDuNd9lCdA7chyyquDXw", "linux"),
        yt("l1t", "Level1Techs", "UC4w1YQAJMWOz4qtxinq55LQ", "linux", enabled = false),
        // Smart home
        news("ha", "Home Assistant blog", "https://www.home-assistant.io/atom.xml", "home-assistant.io", "smarthome"),
        reddit("homeassistant", "smarthome"),
        // Electronics & makers
        news("hackaday", "Hackaday", "https://hackaday.com/blog/feed/", "hackaday.com", "maker"),
        news("cnx", "CNX Software", "https://www.cnx-software.com/feed/", "cnx-software.com", "maker"),
        news("hackster", "Hackster.io", "https://www.hackster.io/news.atom", "hackster.io", "maker"),
        news("rpi", "Raspberry Pi news", "https://www.raspberrypi.com/news/feed/", "raspberrypi.com", "maker"),
        news("geerling", "Jeff Geerling", "https://www.jeffgeerling.com/blog.xml", "jeffgeerling.com", "maker"),
        news("meshtastic", "Meshtastic blog", "https://meshtastic.org/blog/rss.xml", "meshtastic.org", "maker"),
        news("adafruit", "Adafruit blog", "https://blog.adafruit.com/feed/", "adafruit.com", "maker", enabled = false),
        reddit("esp32", "maker"),
        reddit("meshtastic", "maker"),
        reddit("arduino", "maker", enabled = false),
        // 3D printing
        reddit("BambuLab", "printing"),
        reddit("3Dprinting", "printing"),
        yt("cnckitchen", "CNC Kitchen", "UCiczXOhGpvoQGhOL16EZiTg", "printing"),
        news("3dpi", "3D Printing Industry", "https://3dprintingindustry.com/feed/", "3dprintingindustry.com", "printing", enabled = false),
        // Gaming
        news("pcgamer", "PC Gamer", "https://www.pcgamer.com/rss/", "pcgamer.com", "gaming"),
        news("rps", "Rock Paper Shotgun", "https://www.rockpapershotgun.com/feed", "rockpapershotgun.com", "gaming"),
        news("eurogamer", "Eurogamer", "https://www.eurogamer.net/feed", "eurogamer.net", "gaming", enabled = false),
        gh("openfront", "OpenFront releases", "openfrontio/OpenFrontIO", "gaming"),
        steam("7dtd", "7 Days to Die", 251570, "gaming"),
        steam("satisfactory", "Satisfactory", 526870, "gaming"),
        steam("nms", "No Man's Sky", 275850, "gaming"),
        steam("schedule1", "Schedule I", 3164500, "gaming"),
        yt("spiffing", "The Spiffing Brit", "UCRHXUZ0BxbkU2MYZgsuFgkQ", "gaming"),
        // Retro gaming
        news("timeext", "Time Extension", "https://www.timeextension.com/feeds/latest", "timeextension.com", "retro"),
        news("retrorgb", "RetroRGB", "https://www.retrorgb.com/feed", "retrorgb.com", "retro"),
        yt("mvg", "Modern Vintage Gamer", "UCjFaPUcJU1vwk193mnW_w1w", "retro"),
        reddit("retrogaming", "retro"),
        gh("romm", "RomM releases", "rommapp/romm", "retro"),
        // Game of Thrones
        reddit("asoiaf", "got"),
        reddit("HouseOfTheDragon", "got"),
        news("notablog", "George R.R. Martin · Not a Blog", "https://georgerrmartin.com/notablog/feed/", "georgerrmartin.com", "got"),
        news(
            "wic", "Winter is Coming", "https://winteriscoming.net/feed/", "winteriscoming.net", "got",
            include = "thrones|dragon|westeros|targaryen|stark|lannister|martin|seven kingdoms|dunk|egg|asoiaf|iron throne",
        ),
        // Science & space
        news("sciencealert", "ScienceAlert", "https://www.sciencealert.com/feed", "sciencealert.com", "science"),
        news("universetoday", "Universe Today", "https://www.universetoday.com/feed/", "universetoday.com", "science"),
        news("nasa", "NASA", "https://www.nasa.gov/news-release/feed/", "nasa.gov", "science"),
        news("newscientist", "New Scientist", "https://www.newscientist.com/feed/home/", "newscientist.com", "science", enabled = false),
        news("spacecom", "Space.com", "https://www.space.com/feeds/all", "space.com", "science"),
        news("phys", "Phys.org", "https://phys.org/rss-feed/", "phys.org", "science", enabled = false),
        yt("veritasium", "Veritasium", "UCHnyfMqiRRG1u-2MsSQLbXA", "science"),
        yt("techconn", "Technology Connections", "UCy0tKL1T7wFoYcxCe0xjN6Q", "science", enabled = false),
        // Military & history
        news("twz", "The War Zone", "https://www.twz.com/feed", "twz.com", "military"),
        news("historynet", "HistoryNet", "https://www.historynet.com/feed/", "historynet.com", "military"),
        news("smithsonian", "Smithsonian Magazine", "https://www.smithsonianmag.com/rss/latest_articles/", "smithsonianmag.com", "military", enabled = false),
        yt("tankmuseum", "The Tank Museum", "UChl-XKVVBAzoEVsnbOfpcqw", "military"),
        // Privacy & security
        news("404", "404 Media", "https://www.404media.co/rss/", "404media.co", "security"),
        news("eff", "EFF Deeplinks", "https://www.eff.org/rss/updates.xml", "eff.org", "security", weight = 0.8f),
        news("krebs", "Krebs on Security", "https://krebsonsecurity.com/feed/", "krebsonsecurity.com", "security"),
        news("bleeping", "BleepingComputer", "https://www.bleepingcomputer.com/feed/", "bleepingcomputer.com", "security"),
        news("techdirt", "Techdirt", "https://www.techdirt.com/feed/", "techdirt.com", "security", enabled = false),
        yt("defcon", "DEF CON", "UC6Om9kAkl32dWlDSNlDS9Iw", "security", weight = 0.6f),
        yt("s2", "S2 Underground", "UCTq1zHztiV69Ur8t6jco4CQ", "security", enabled = false),
        // Internet culture
        yt("coffeezilla", "Coffeezilla", "UCFQMnBA3CS502aghlcr0_aw", "internet"),
        yt("wendigoon", "Wendigoon", "UC3cpN6gcJQqcCM6mxRUo_dA", "internet"),
        news("kym", "Know Your Meme", "https://knowyourmeme.com/news.rss", "knowyourmeme.com", "internet"),
        // Comedy
        yt("taskmaster", "Taskmaster", "UCT5C7yaO3RVuOgwP8JVAujQ", "comedy"),
        yt("soresi", "Gianmarco Soresi", "UCCYWagpWEequTwx61dYGT0w", "comedy"),
        // TV & film
        news("slashfilm", "/Film", "https://www.slashfilm.com/feed/", "slashfilm.com", "screen", weight = 0.7f),
        gnews(
            "shows", "Google News · Your shows",
            "\"House of the Dragon\" OR \"The Rookie\" OR \"Reacher\" OR \"Tulsa King\" OR \"Mayor of Kingstown\" when:7d",
            "screen",
        ),
        // Deals
        Source("ozb_computing", "OzBargain · Computing", SourceType.DEALS, "https://www.ozbargain.com.au/cat/computing/feed", "deals", "ozbargain.com.au"),
        Source("ozb_gaming", "OzBargain · Gaming", SourceType.DEALS, "https://www.ozbargain.com.au/cat/gaming/feed", "deals", "ozbargain.com.au"),
        Source("ozb_electronics", "OzBargain · Electronics", SourceType.DEALS, "https://www.ozbargain.com.au/cat/electrical-electronics/feed", "deals", "ozbargain.com.au", enabled = false),
        Source("ozb_all", "OzBargain · All deals", SourceType.DEALS, "https://www.ozbargain.com.au/deals/feed", "deals", "ozbargain.com.au", enabled = false),
        // EVs & e-bikes
        news("electrek", "Electrek", "https://electrek.co/feed/", "electrek.co", "ev", weight = 0.7f),
        gnews("ebikes_au", "Google News · E-bikes in Australia", "electric motorbike OR e-bike Australia when:7d", "ev", enabled = false),
        // Transport & logistics
        news("bigrigs", "Big Rigs", "https://www.bigrigs.com.au/feed/", "bigrigs.com.au", "transport", weight = 0.6f),
        news("primemover", "Prime Mover", "https://www.primemovermag.com.au/rss", "primemovermag.com.au", "transport", enabled = false),
    )

    val sourceById = sources.associateBy { it.id }
}
