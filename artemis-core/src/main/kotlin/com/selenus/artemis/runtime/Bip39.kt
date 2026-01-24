package com.selenus.artemis.runtime

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Artemis BIP39 Implementation
 * 
 * Provides mnemonic phrase generation, validation, and seed derivation
 * with full support for 12-word (128-bit) and 24-word (256-bit) phrases.
 * 
 * This is a complete, standalone implementation that doesn't depend on
 * external BIP39 libraries, designed for the Artemis Solana SDK.
 * 
 * Standards:
 * - BIP-39: Mnemonic code for generating deterministic keys
 * - Uses PBKDF2 with HMAC-SHA512 (2048 iterations) for seed derivation
 */
object Bip39 {
    
    /**
     * Supported mnemonic phrase lengths with their entropy sizes.
     */
    enum class WordCount(val count: Int, val entropyBits: Int, val checksumBits: Int) {
        TWELVE(12, 128, 4),
        FIFTEEN(15, 160, 5),
        EIGHTEEN(18, 192, 6),
        TWENTY_ONE(21, 224, 7),
        TWENTY_FOUR(24, 256, 8);
        
        val totalBits: Int get() = entropyBits + checksumBits
        val entropyBytes: Int get() = entropyBits / 8
        
        companion object {
            fun fromCount(count: Int): WordCount = entries.find { it.count == count }
                ?: throw IllegalArgumentException("Invalid word count: $count. Must be 12, 15, 18, 21, or 24")
        }
    }
    
    /**
     * Result of mnemonic generation, containing both the phrase and raw entropy.
     */
    data class MnemonicResult(
        val phrase: String,
        val words: List<String>,
        val entropy: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MnemonicResult) return false
            return phrase == other.phrase && entropy.contentEquals(other.entropy)
        }
        
        override fun hashCode(): Int = 31 * phrase.hashCode() + entropy.contentHashCode()
    }
    
    /**
     * Generates a new random mnemonic phrase.
     * 
     * @param wordCount The number of words (12, 15, 18, 21, or 24). Default is 12.
     * @param random The secure random source. Default uses system SecureRandom.
     * @return The generated mnemonic result.
     */
    fun generate(
        wordCount: WordCount = WordCount.TWELVE,
        random: SecureRandom = SecureRandom()
    ): MnemonicResult {
        val entropy = ByteArray(wordCount.entropyBytes)
        random.nextBytes(entropy)
        return entropyToMnemonic(entropy)
    }
    
    /**
     * Generates a 12-word mnemonic phrase.
     */
    fun generate12Words(random: SecureRandom = SecureRandom()): MnemonicResult {
        return generate(WordCount.TWELVE, random)
    }
    
    /**
     * Generates a 24-word mnemonic phrase.
     */
    fun generate24Words(random: SecureRandom = SecureRandom()): MnemonicResult {
        return generate(WordCount.TWENTY_FOUR, random)
    }
    
    /**
     * Converts raw entropy bytes to a mnemonic phrase.
     * 
     * @param entropy The entropy bytes (16, 20, 24, 28, or 32 bytes for 12-24 words).
     * @return The mnemonic result.
     */
    fun entropyToMnemonic(entropy: ByteArray): MnemonicResult {
        val wordCount = when (entropy.size) {
            16 -> WordCount.TWELVE
            20 -> WordCount.FIFTEEN
            24 -> WordCount.EIGHTEEN
            28 -> WordCount.TWENTY_ONE
            32 -> WordCount.TWENTY_FOUR
            else -> throw IllegalArgumentException(
                "Invalid entropy length: ${entropy.size} bytes. Must be 16, 20, 24, 28, or 32"
            )
        }
        
        // Calculate checksum (first bits of SHA-256 hash)
        val hash = Crypto.sha256(entropy)
        val checksumByte = hash[0].toInt() and 0xFF
        
        // Convert entropy + checksum to 11-bit words
        val bits = StringBuilder()
        for (b in entropy) {
            bits.append(String.format("%8s", Integer.toBinaryString(b.toInt() and 0xFF)).replace(' ', '0'))
        }
        // Add checksum bits
        val checksumBits = String.format("%8s", Integer.toBinaryString(checksumByte)).replace(' ', '0')
        bits.append(checksumBits.substring(0, wordCount.checksumBits))
        
        val words = ArrayList<String>(wordCount.count)
        for (i in 0 until wordCount.count) {
            val start = i * 11
            val end = start + 11
            val index = Integer.parseInt(bits.substring(start, end), 2)
            words.add(WORDLIST[index])
        }
        
        return MnemonicResult(
            phrase = words.joinToString(" "),
            words = words,
            entropy = entropy.copyOf()
        )
    }
    
    /**
     * Validates a mnemonic phrase.
     * 
     * @param phrase The mnemonic phrase (space-separated words).
     * @return True if the phrase is valid, false otherwise.
     */
    fun isValid(phrase: String): Boolean {
        return try {
            validate(phrase)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Validates a mnemonic phrase and throws if invalid.
     * 
     * @param phrase The mnemonic phrase.
     * @throws IllegalArgumentException if the phrase is invalid.
     */
    fun validate(phrase: String) {
        val words = normalizePhrase(phrase)
        val wordCount = try {
            WordCount.fromCount(words.size)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid mnemonic length: ${words.size} words")
        }
        
        // Validate each word exists in wordlist
        val indices = words.map { word ->
            val index = WORDLIST.binarySearch(word)
            if (index < 0) {
                throw IllegalArgumentException("Invalid mnemonic word: '$word'")
            }
            index
        }
        
        // Reconstruct bits from word indices
        val bits = StringBuilder()
        for (index in indices) {
            bits.append(String.format("%11s", Integer.toBinaryString(index)).replace(' ', '0'))
        }
        
        // Extract entropy and checksum
        val entropyBits = bits.substring(0, wordCount.entropyBits)
        val checksumBits = bits.substring(wordCount.entropyBits)
        
        // Convert entropy bits to bytes
        val entropy = ByteArray(wordCount.entropyBytes)
        for (i in entropy.indices) {
            entropy[i] = Integer.parseInt(entropyBits.substring(i * 8, (i + 1) * 8), 2).toByte()
        }
        
        // Verify checksum
        val hash = Crypto.sha256(entropy)
        val expectedChecksumByte = hash[0].toInt() and 0xFF
        val expectedChecksum = String.format("%8s", Integer.toBinaryString(expectedChecksumByte))
            .replace(' ', '0')
            .substring(0, wordCount.checksumBits)
        
        if (checksumBits != expectedChecksum) {
            throw IllegalArgumentException("Invalid mnemonic checksum")
        }
    }
    
    /**
     * Converts a mnemonic phrase to a BIP-39 seed.
     * 
     * @param phrase The mnemonic phrase.
     * @param passphrase Optional passphrase (default is empty string).
     * @return The 64-byte seed suitable for key derivation.
     */
    fun toSeed(phrase: String, passphrase: String = ""): ByteArray {
        validate(phrase) // Ensure valid before deriving
        
        val normalizedPhrase = normalizePhrase(phrase).joinToString(" ")
        val salt = "mnemonic$passphrase"
        
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val spec = PBEKeySpec(
            normalizedPhrase.toCharArray(),
            salt.toByteArray(Charsets.UTF_8),
            2048,
            512
        )
        
        return factory.generateSecret(spec).encoded
    }
    
    /**
     * Converts a mnemonic phrase directly to a Solana Keypair.
     * Uses the default Solana derivation path (first 32 bytes of seed).
     * 
     * For more control over derivation paths, use toSeed() with Bip32.
     * 
     * @param phrase The mnemonic phrase.
     * @param passphrase Optional passphrase.
     * @return A Solana-compatible Keypair.
     */
    fun toKeypair(phrase: String, passphrase: String = ""): Keypair {
        val seed = toSeed(phrase, passphrase)
        // Solana uses the first 32 bytes of the BIP-39 seed for direct derivation
        return Keypair.fromSeed(seed.copyOfRange(0, 32))
    }
    
    /**
     * Extracts the raw entropy from a mnemonic phrase.
     * 
     * @param phrase The mnemonic phrase.
     * @return The raw entropy bytes.
     */
    fun toEntropy(phrase: String): ByteArray {
        val words = normalizePhrase(phrase)
        val wordCount = WordCount.fromCount(words.size)
        
        val indices = words.map { word ->
            val index = WORDLIST.binarySearch(word)
            require(index >= 0) { "Invalid mnemonic word: '$word'" }
            index
        }
        
        val bits = StringBuilder()
        for (index in indices) {
            bits.append(String.format("%11s", Integer.toBinaryString(index)).replace(' ', '0'))
        }
        
        val entropyBits = bits.substring(0, wordCount.entropyBits)
        val entropy = ByteArray(wordCount.entropyBytes)
        for (i in entropy.indices) {
            entropy[i] = Integer.parseInt(entropyBits.substring(i * 8, (i + 1) * 8), 2).toByte()
        }
        
        return entropy
    }
    
    /**
     * Gets a word from the BIP-39 English wordlist by index.
     */
    fun getWord(index: Int): String {
        require(index in 0..2047) { "Word index must be 0-2047" }
        return WORDLIST[index]
    }
    
    /**
     * Gets the index of a word in the BIP-39 English wordlist.
     * @return The index (0-2047), or -1 if not found.
     */
    fun getWordIndex(word: String): Int {
        return WORDLIST.binarySearch(word.lowercase().trim())
    }
    
    /**
     * Gets words that match a prefix, useful for autocomplete.
     * 
     * @param prefix The prefix to match.
     * @param maxResults Maximum results to return.
     * @return List of matching words.
     */
    fun findWordsWithPrefix(prefix: String, maxResults: Int = 10): List<String> {
        val normalized = prefix.lowercase().trim()
        if (normalized.isEmpty()) return emptyList()
        
        return WORDLIST.filter { it.startsWith(normalized) }.take(maxResults)
    }
    
    private fun normalizePhrase(phrase: String): List<String> {
        return phrase.trim()
            .lowercase()
            .split("\\s+".toRegex())
            .filter { it.isNotEmpty() }
    }
    
    /**
     * BIP-39 English wordlist (2048 words).
     * Sorted alphabetically for binary search support.
     */
    val WORDLIST: List<String> = listOf(
        "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract",
        "absurd", "abuse", "access", "accident", "account", "accuse", "achieve", "acid",
        "acoustic", "acquire", "across", "act", "action", "actor", "actress", "actual",
        "adapt", "add", "addict", "address", "adjust", "admit", "adult", "advance",
        "advice", "aerobic", "affair", "afford", "afraid", "again", "age", "agent",
        "agree", "ahead", "aim", "air", "airport", "aisle", "alarm", "album",
        "alcohol", "alert", "alien", "all", "alley", "allow", "almost", "alone",
        "alpha", "already", "also", "alter", "always", "amateur", "amazing", "among",
        "amount", "amused", "analyst", "anchor", "ancient", "anger", "angle", "angry",
        "animal", "ankle", "announce", "annual", "another", "answer", "antenna", "antique",
        "anxiety", "any", "apart", "apology", "appear", "apple", "approve", "april",
        "arch", "arctic", "area", "arena", "argue", "arm", "armed", "armor",
        "army", "around", "arrange", "arrest", "arrive", "arrow", "art", "artefact",
        "artist", "artwork", "ask", "aspect", "assault", "asset", "assist", "assume",
        "asthma", "athlete", "atom", "attack", "attend", "attitude", "attract", "auction",
        "audit", "august", "aunt", "author", "auto", "autumn", "average", "avocado",
        "avoid", "awake", "aware", "away", "awesome", "awful", "awkward", "axis",
        "baby", "bachelor", "bacon", "badge", "bag", "balance", "balcony", "ball",
        "bamboo", "banana", "banner", "bar", "barely", "bargain", "barrel", "base",
        "basic", "basket", "battle", "beach", "bean", "beauty", "because", "become",
        "beef", "before", "begin", "behave", "behind", "believe", "below", "belt",
        "bench", "benefit", "best", "betray", "better", "between", "beyond", "bicycle",
        "bid", "bike", "bind", "biology", "bird", "birth", "bitter", "black",
        "blade", "blame", "blanket", "blast", "bleak", "bless", "blind", "blood",
        "blossom", "blouse", "blue", "blur", "blush", "board", "boat", "body",
        "boil", "bomb", "bone", "bonus", "book", "boost", "border", "boring",
        "borrow", "boss", "bottom", "bounce", "box", "boy", "bracket", "brain",
        "brand", "brass", "brave", "bread", "breeze", "brick", "bridge", "brief",
        "bright", "bring", "brisk", "broccoli", "broken", "bronze", "broom", "brother",
        "brown", "brush", "bubble", "buddy", "budget", "buffalo", "build", "bulb",
        "bulk", "bullet", "bundle", "bunker", "burden", "burger", "burst", "bus",
        "business", "busy", "butter", "buyer", "buzz", "cabbage", "cabin", "cable",
        "cactus", "cage", "cake", "call", "calm", "camera", "camp", "can",
        "canal", "cancel", "candy", "cannon", "canoe", "canvas", "canyon", "capable",
        "capital", "captain", "car", "carbon", "card", "cargo", "carpet", "carry",
        "cart", "case", "cash", "casino", "castle", "casual", "cat", "catalog",
        "catch", "category", "cattle", "caught", "cause", "caution", "cave", "ceiling",
        "celery", "cement", "census", "century", "cereal", "certain", "chair", "chalk",
        "champion", "change", "chaos", "chapter", "charge", "chase", "chat", "cheap",
        "check", "cheese", "chef", "cherry", "chest", "chicken", "chief", "child",
        "chimney", "choice", "choose", "chronic", "chuckle", "chunk", "churn", "cigar",
        "cinnamon", "circle", "citizen", "city", "civil", "claim", "clap", "clarify",
        "claw", "clay", "clean", "clerk", "clever", "click", "client", "cliff",
        "climb", "clinic", "clip", "clock", "clog", "close", "cloth", "cloud",
        "clown", "club", "clump", "cluster", "clutch", "coach", "coast", "coconut",
        "code", "coffee", "coil", "coin", "collect", "color", "column", "combine",
        "come", "comfort", "comic", "common", "company", "concert", "conduct", "confirm",
        "congress", "connect", "consider", "control", "convince", "cook", "cool", "copper",
        "copy", "coral", "core", "corn", "correct", "cost", "cotton", "couch",
        "country", "couple", "course", "cousin", "cover", "coyote", "crack", "cradle",
        "craft", "cram", "crane", "crash", "crater", "crawl", "crazy", "cream",
        "credit", "creek", "crew", "cricket", "crime", "crisp", "critic", "crop",
        "cross", "crouch", "crowd", "crucial", "cruel", "cruise", "crumble", "crunch",
        "crush", "cry", "crystal", "cube", "culture", "cup", "cupboard", "curious",
        "current", "curtain", "curve", "cushion", "custom", "cute", "cycle", "dad",
        "damage", "damp", "dance", "danger", "daring", "dash", "daughter", "dawn",
        "day", "deal", "debate", "debris", "decade", "december", "decide", "decline",
        "decorate", "decrease", "deer", "defense", "define", "defy", "degree", "delay",
        "deliver", "demand", "demise", "denial", "dentist", "deny", "depart", "depend",
        "deposit", "depth", "deputy", "derive", "describe", "desert", "design", "desk",
        "despair", "destroy", "detail", "detect", "develop", "device", "devote", "diagram",
        "dial", "diamond", "diary", "dice", "diesel", "diet", "differ", "digital",
        "dignity", "dilemma", "dinner", "dinosaur", "direct", "dirt", "disagree", "discover",
        "disease", "dish", "dismiss", "disorder", "display", "distance", "divert", "divide",
        "divorce", "dizzy", "doctor", "document", "dog", "doll", "dolphin", "domain",
        "donate", "donkey", "donor", "door", "dose", "double", "dove", "draft",
        "dragon", "drama", "drastic", "draw", "dream", "dress", "drift", "drill",
        "drink", "drip", "drive", "drop", "drum", "dry", "duck", "dumb",
        "dune", "during", "dust", "dutch", "duty", "dwarf", "dynamic", "eager",
        "eagle", "early", "earn", "earth", "easily", "east", "easy", "echo",
        "ecology", "economy", "edge", "edit", "educate", "effort", "egg", "eight",
        "either", "elbow", "elder", "electric", "elegant", "element", "elephant", "elevator",
        "elite", "else", "embark", "embody", "embrace", "emerge", "emotion", "employ",
        "empower", "empty", "enable", "enact", "end", "endless", "endorse", "enemy",
        "energy", "enforce", "engage", "engine", "enhance", "enjoy", "enlist", "enough",
        "enrich", "enroll", "ensure", "enter", "entire", "entry", "envelope", "episode",
        "equal", "equip", "era", "erase", "erode", "erosion", "error", "erupt",
        "escape", "essay", "essence", "estate", "eternal", "ethics", "evidence", "evil",
        "evoke", "evolve", "exact", "example", "excess", "exchange", "excite", "exclude",
        "excuse", "execute", "exercise", "exhaust", "exhibit", "exile", "exist", "exit",
        "exotic", "expand", "expect", "expire", "explain", "expose", "express", "extend",
        "extra", "eye", "eyebrow", "fabric", "face", "faculty", "fade", "faint",
        "faith", "fall", "false", "fame", "family", "famous", "fan", "fancy",
        "fantasy", "farm", "fashion", "fat", "fatal", "father", "fatigue", "fault",
        "favorite", "feature", "february", "federal", "fee", "feed", "feel", "female",
        "fence", "festival", "fetch", "fever", "few", "fiber", "fiction", "field",
        "figure", "file", "film", "filter", "final", "find", "fine", "finger",
        "finish", "fire", "firm", "first", "fiscal", "fish", "fit", "fitness",
        "fix", "flag", "flame", "flash", "flat", "flavor", "flee", "flight",
        "flip", "float", "flock", "floor", "flower", "fluid", "flush", "fly",
        "foam", "focus", "fog", "foil", "fold", "follow", "food", "foot",
        "force", "forest", "forget", "fork", "fortune", "forum", "forward", "fossil",
        "foster", "found", "fox", "fragile", "frame", "frequent", "fresh", "friend",
        "fringe", "frog", "front", "frost", "frown", "frozen", "fruit", "fuel",
        "fun", "funny", "furnace", "fury", "future", "gadget", "gain", "galaxy",
        "gallery", "game", "gap", "garage", "garbage", "garden", "garlic", "garment",
        "gas", "gasp", "gate", "gather", "gauge", "gaze", "general", "genius",
        "genre", "gentle", "genuine", "gesture", "ghost", "giant", "gift", "giggle",
        "ginger", "giraffe", "girl", "give", "glad", "glance", "glare", "glass",
        "glide", "glimpse", "globe", "gloom", "glory", "glove", "glow", "glue",
        "goat", "goddess", "gold", "good", "goose", "gorilla", "gospel", "gossip",
        "govern", "gown", "grab", "grace", "grain", "grant", "grape", "grass",
        "gravity", "great", "green", "grid", "grief", "grit", "grocery", "group",
        "grow", "grunt", "guard", "guess", "guide", "guilt", "guitar", "gun",
        "gym", "habit", "hair", "half", "hammer", "hamster", "hand", "happy",
        "harbor", "hard", "harsh", "harvest", "hat", "have", "hawk", "hazard",
        "head", "health", "heart", "heavy", "hedgehog", "height", "hello", "helmet",
        "help", "hen", "hero", "hidden", "high", "hill", "hint", "hip",
        "hire", "history", "hobby", "hockey", "hold", "hole", "holiday", "hollow",
        "home", "honey", "hood", "hope", "horn", "horror", "horse", "hospital",
        "host", "hotel", "hour", "hover", "hub", "huge", "human", "humble",
        "humor", "hundred", "hungry", "hunt", "hurdle", "hurry", "hurt", "husband",
        "hybrid", "ice", "icon", "idea", "identify", "idle", "ignore", "ill",
        "illegal", "illness", "image", "imitate", "immense", "immune", "impact", "impose",
        "improve", "impulse", "inch", "include", "income", "increase", "index", "indicate",
        "indoor", "industry", "infant", "inflict", "inform", "inhale", "inherit", "initial",
        "inject", "injury", "inmate", "inner", "innocent", "input", "inquiry", "insane",
        "insect", "inside", "inspire", "install", "intact", "interest", "into", "invest",
        "invite", "involve", "iron", "island", "isolate", "issue", "item", "ivory",
        "jacket", "jaguar", "jar", "jazz", "jealous", "jeans", "jelly", "jewel",
        "job", "join", "joke", "journey", "joy", "judge", "juice", "jump",
        "jungle", "junior", "junk", "just", "kangaroo", "keen", "keep", "ketchup",
        "key", "kick", "kid", "kidney", "kind", "kingdom", "kiss", "kit",
        "kitchen", "kite", "kitten", "kiwi", "knee", "knife", "knock", "know",
        "lab", "label", "labor", "ladder", "lady", "lake", "lamp", "language",
        "laptop", "large", "later", "latin", "laugh", "laundry", "lava", "law",
        "lawn", "lawsuit", "layer", "lazy", "leader", "leaf", "learn", "leave",
        "lecture", "left", "leg", "legal", "legend", "leisure", "lemon", "lend",
        "length", "lens", "leopard", "lesson", "letter", "level", "liar", "liberty",
        "library", "license", "life", "lift", "light", "like", "limb", "limit",
        "link", "lion", "liquid", "list", "little", "live", "lizard", "load",
        "loan", "lobster", "local", "lock", "logic", "lonely", "long", "loop",
        "lottery", "loud", "lounge", "love", "loyal", "lucky", "luggage", "lumber",
        "lunar", "lunch", "luxury", "lyrics", "machine", "mad", "magic", "magnet",
        "maid", "mail", "main", "major", "make", "mammal", "man", "manage",
        "mandate", "mango", "mansion", "manual", "maple", "marble", "march", "margin",
        "marine", "market", "marriage", "mask", "mass", "master", "match", "material",
        "math", "matrix", "matter", "maximum", "maze", "meadow", "mean", "measure",
        "meat", "mechanic", "medal", "media", "melody", "melt", "member", "memory",
        "mention", "menu", "mercy", "merge", "merit", "merry", "mesh", "message",
        "metal", "method", "middle", "midnight", "milk", "million", "mimic", "mind",
        "minimum", "minor", "minute", "miracle", "mirror", "misery", "miss", "mistake",
        "mix", "mixed", "mixture", "mobile", "model", "modify", "mom", "moment",
        "monitor", "monkey", "monster", "month", "moon", "moral", "more", "morning",
        "mosquito", "mother", "motion", "motor", "mountain", "mouse", "move", "movie",
        "much", "muffin", "mule", "multiply", "muscle", "museum", "mushroom", "music",
        "must", "mutual", "myself", "mystery", "myth", "naive", "name", "napkin",
        "narrow", "nasty", "nation", "nature", "near", "neck", "need", "negative",
        "neglect", "neither", "nephew", "nerve", "nest", "net", "network", "neutral",
        "never", "news", "next", "nice", "night", "noble", "noise", "nominee",
        "noodle", "normal", "north", "nose", "notable", "note", "nothing", "notice",
        "novel", "now", "nuclear", "number", "nurse", "nut", "oak", "obey",
        "object", "oblige", "obscure", "observe", "obtain", "obvious", "occur", "ocean",
        "october", "odor", "off", "offer", "office", "often", "oil", "okay",
        "old", "olive", "olympic", "omit", "once", "one", "onion", "online",
        "only", "open", "opera", "opinion", "oppose", "option", "orange", "orbit",
        "orchard", "order", "ordinary", "organ", "orient", "original", "orphan", "ostrich",
        "other", "outdoor", "outer", "output", "outside", "oval", "oven", "over",
        "own", "owner", "oxygen", "oyster", "ozone", "pact", "paddle", "page",
        "pair", "palace", "palm", "panda", "panel", "panic", "panther", "paper",
        "parade", "parent", "park", "parrot", "party", "pass", "patch", "path",
        "patient", "patrol", "pattern", "pause", "pave", "payment", "peace", "peanut",
        "pear", "peasant", "pelican", "pen", "penalty", "pencil", "people", "pepper",
        "perfect", "permit", "person", "pet", "phone", "photo", "phrase", "physical",
        "piano", "picnic", "picture", "piece", "pig", "pigeon", "pill", "pilot",
        "pink", "pioneer", "pipe", "pistol", "pitch", "pizza", "place", "planet",
        "plastic", "plate", "play", "please", "pledge", "pluck", "plug", "plunge",
        "poem", "poet", "point", "polar", "pole", "police", "pond", "pony",
        "pool", "popular", "portion", "position", "possible", "post", "potato", "pottery",
        "poverty", "powder", "power", "practice", "praise", "predict", "prefer", "prepare",
        "present", "pretty", "prevent", "price", "pride", "primary", "print", "priority",
        "prison", "private", "prize", "problem", "process", "produce", "profit", "program",
        "project", "promote", "proof", "property", "prosper", "protect", "proud", "provide",
        "public", "pudding", "pull", "pulp", "pulse", "pumpkin", "punch", "pupil",
        "puppy", "purchase", "purity", "purpose", "purse", "push", "put", "puzzle",
        "pyramid", "quality", "quantum", "quarter", "question", "quick", "quit", "quiz",
        "quote", "rabbit", "raccoon", "race", "rack", "radar", "radio", "rail",
        "rain", "raise", "rally", "ramp", "ranch", "random", "range", "rapid",
        "rare", "rate", "rather", "raven", "raw", "razor", "ready", "real",
        "reason", "rebel", "rebuild", "recall", "receive", "recipe", "record", "recycle",
        "reduce", "reflect", "reform", "refuse", "region", "regret", "regular", "reject",
        "relax", "release", "relief", "rely", "remain", "remember", "remind", "remove",
        "render", "renew", "rent", "reopen", "repair", "repeat", "replace", "report",
        "require", "rescue", "resemble", "resist", "resource", "response", "result", "retire",
        "retreat", "return", "reunion", "reveal", "review", "reward", "rhythm", "rib",
        "ribbon", "rice", "rich", "ride", "ridge", "rifle", "right", "rigid",
        "ring", "riot", "ripple", "risk", "ritual", "rival", "river", "road",
        "roast", "robot", "robust", "rocket", "romance", "roof", "rookie", "room",
        "rose", "rotate", "rough", "round", "route", "royal", "rubber", "rude",
        "rug", "rule", "run", "runway", "rural", "sad", "saddle", "sadness",
        "safe", "sail", "salad", "salmon", "salon", "salt", "salute", "same",
        "sample", "sand", "satisfy", "satoshi", "sauce", "sausage", "save", "say",
        "scale", "scan", "scare", "scatter", "scene", "scheme", "school", "science",
        "scissors", "scorpion", "scout", "scrap", "screen", "script", "scrub", "sea",
        "search", "season", "seat", "second", "secret", "section", "security", "seed",
        "seek", "segment", "select", "sell", "seminar", "senior", "sense", "sentence",
        "series", "service", "session", "settle", "setup", "seven", "shadow", "shaft",
        "shallow", "share", "shed", "shell", "sheriff", "shield", "shift", "shine",
        "ship", "shiver", "shock", "shoe", "shoot", "shop", "short", "shoulder",
        "shove", "shrimp", "shrug", "shuffle", "shy", "sibling", "sick", "side",
        "siege", "sight", "sign", "silent", "silk", "silly", "silver", "similar",
        "simple", "since", "sing", "siren", "sister", "situate", "six", "size",
        "skate", "sketch", "ski", "skill", "skin", "skirt", "skull", "slab",
        "slam", "sleep", "slender", "slice", "slide", "slight", "slim", "slogan",
        "slot", "slow", "slush", "small", "smart", "smile", "smoke", "smooth",
        "snack", "snake", "snap", "sniff", "snow", "soap", "soccer", "social",
        "sock", "soda", "soft", "solar", "soldier", "solid", "solution", "solve",
        "someone", "song", "soon", "sorry", "sort", "soul", "sound", "soup",
        "source", "south", "space", "spare", "spatial", "spawn", "speak", "special",
        "speed", "spell", "spend", "sphere", "spice", "spider", "spike", "spin",
        "spirit", "split", "spoil", "sponsor", "spoon", "sport", "spot", "spray",
        "spread", "spring", "spy", "square", "squeeze", "squirrel", "stable", "stadium",
        "staff", "stage", "stairs", "stamp", "stand", "start", "state", "stay",
        "steak", "steel", "stem", "step", "stereo", "stick", "still", "sting",
        "stock", "stomach", "stone", "stool", "story", "stove", "strategy", "street",
        "strike", "strong", "struggle", "student", "stuff", "stumble", "style", "subject",
        "submit", "subway", "success", "such", "sudden", "suffer", "sugar", "suggest",
        "suit", "summer", "sun", "sunny", "sunset", "super", "supply", "supreme",
        "sure", "surface", "surge", "surprise", "surround", "survey", "suspect", "sustain",
        "swallow", "swamp", "swap", "swarm", "swear", "sweet", "swift", "swim",
        "swing", "switch", "sword", "symbol", "symptom", "syrup", "system", "table",
        "tackle", "tag", "tail", "talent", "talk", "tank", "tape", "target",
        "task", "taste", "tattoo", "taxi", "teach", "team", "tell", "ten",
        "tenant", "tennis", "tent", "term", "test", "text", "thank", "that",
        "theme", "then", "theory", "there", "they", "thing", "this", "thought",
        "three", "thrive", "throw", "thumb", "thunder", "ticket", "tide", "tiger",
        "tilt", "timber", "time", "tiny", "tip", "tired", "tissue", "title",
        "toast", "tobacco", "today", "toddler", "toe", "together", "toilet", "token",
        "tomato", "tomorrow", "tone", "tongue", "tonight", "tool", "tooth", "top",
        "topic", "topple", "torch", "tornado", "tortoise", "toss", "total", "tourist",
        "toward", "tower", "town", "toy", "track", "trade", "traffic", "tragic",
        "train", "transfer", "trap", "trash", "travel", "tray", "treat", "tree",
        "trend", "trial", "tribe", "trick", "trigger", "trim", "trip", "trophy",
        "trouble", "truck", "true", "truly", "trumpet", "trust", "truth", "try",
        "tube", "tuition", "tumble", "tuna", "tunnel", "turkey", "turn", "turtle",
        "twelve", "twenty", "twice", "twin", "twist", "two", "type", "typical",
        "ugly", "umbrella", "unable", "unaware", "uncle", "uncover", "under", "undo",
        "unfair", "unfold", "unhappy", "uniform", "unique", "unit", "universe", "unknown",
        "unlock", "until", "unusual", "unveil", "update", "upgrade", "uphold", "upon",
        "upper", "upset", "urban", "urge", "usage", "use", "used", "useful",
        "useless", "usual", "utility", "vacant", "vacuum", "vague", "valid", "valley",
        "valve", "van", "vanish", "vapor", "various", "vast", "vault", "vehicle",
        "velvet", "vendor", "venture", "venue", "verb", "verify", "version", "very",
        "vessel", "veteran", "viable", "vibrant", "vicious", "victory", "video", "view",
        "village", "vintage", "violin", "virtual", "virus", "visa", "visit", "visual",
        "vital", "vivid", "vocal", "voice", "void", "volcano", "volume", "vote",
        "voyage", "wage", "wagon", "wait", "walk", "wall", "walnut", "want",
        "warfare", "warm", "warrior", "wash", "wasp", "waste", "water", "wave",
        "way", "wealth", "weapon", "wear", "weasel", "weather", "web", "wedding",
        "weekend", "weird", "welcome", "west", "wet", "whale", "what", "wheat",
        "wheel", "when", "where", "whip", "whisper", "wide", "width", "wife",
        "wild", "will", "win", "window", "wine", "wing", "wink", "winner",
        "winter", "wire", "wisdom", "wise", "wish", "witness", "wolf", "woman",
        "wonder", "wood", "wool", "word", "work", "world", "worry", "worth",
        "wrap", "wreck", "wrestle", "wrist", "write", "wrong", "yard", "year",
        "yellow", "you", "young", "youth", "zebra", "zero", "zone", "zoo"
    )
}
