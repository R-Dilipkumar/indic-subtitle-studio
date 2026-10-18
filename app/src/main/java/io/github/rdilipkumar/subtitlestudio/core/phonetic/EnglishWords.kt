package io.github.rdilipkumar.subtitlestudio.core.phonetic

import io.github.rdilipkumar.subtitlestudio.core.model.Script

/**
 * Finds English words that were spoken in an Indian-language video and written in that
 * language's letters ("சூப்பர்", "फ़ोन"), so the English-letters line can spell them the English
 * way ("super", "phone") instead of by sound ("sooppar", "fon").
 *
 * It compares sounds, not spellings: both the heard word (already in English letters) and the
 * listed pronunciations are boiled down to a rough sound key (voiced and plain stops alike,
 * "f" like "p", vowels loose, the extra vowels Indian scripts add to clusters ignored). Only
 * common words of Indian everyday talk are listed, and words that are also native words are
 * left out, so a native word is very rarely mistaken for English.
 */
object EnglishWords {

  /** English word = how Indians say it, in plain letters (several ways after a slash). */
  private const val LIST = """
hello = helo/hallo
okay = oke/okey
sorry = saari/sorri
thanks = tenks/thanks
please = pliis
welcome = velkam
guys = kais
friend = frend/frent/prend
friends = frends/prends
bro = bro
brother = brathar
sister = sistar
uncle = ankil
aunty = aanti
super = soopar/supar
superb = sooparp
nice = nais
best = best
better = petar
great = kret
awesome = aasam
amazing = amesing
cool = kuul
mass = maas
funny = paani
crazy = kresi
simple = simpil
normal = naarmal/normal/naarmall
normally = naarmali/normali
special = speshal
different = tiparent/difrent
same = sem
really = riyali/riili
actually = aakchuvali/akchuali
basically = pesikali
exactly = eksaktli
totally = tottali
literally = litrali
seriously = siiriyasli
finally = painali
first = parst/farst
last = laast
next = nekst
full = pul
half = haap/haaf
double = tapul/dabul
single = sinkil
extra = ekstra
food = food/pood
foodie = foodi
taste = test/teyst/tesht/tes
tasty = testi
menu = menu
order = aartar/aardar
dish = tish
plate = plet
spoon = spuun
chicken = sikkan
mutton = mattan
fish = pish
egg = ek
rice = rais
pizza = pissa
burger = parkar/barkar
sandwich = sendvich/sandvich
juice = juus
coffee = kaapi
ice = ais
icecream = aiskriim
cream = kriim
chocolate = saaklet/chaaklet/chokalet/chaakalet
vanilla = venila/vanila
strawberry = straaperi/strawberi
mango = menko/mango
butter = pattar/battar
butterscotch = pattarskaach/battarskach
cheese = chiis
cake = kek
dessert = tesart/desart
sweet = sviit
spicy = spaisi
sugar = suukar/shugar
fruit = pruut
fruits = pruuts
nuts = nats
shake = shek
milkshake = milkshek
sauce = saas
soup = suup
starter = staartar
lunch = lanch
dinner = tinnar/dinar
breakfast = prekpast
snacks = sneks/snaks
hotel = hottal/hotal
restaurant = restaarant/restorent
shop = shaap
cafe = kepe/kafe
bakery = pekari
counter = kavuntar/kauntar
bill = pil
price = prais/pirais
rate = ret
cost = kaast/kost
offer = aapar/aafar
discount = tiskavunt/diskaunt
premium = preemiyam/premiyam/pireemiyam
cheap = chiip
quality = kvaaliti/kvaliti
quantity = kvaantiti/kvantiti
service = sarvis
staff = staap/staff
parcel = paarsal
delivery = teliveri/deliveri
combo = kaampo/kombo
item = aitam
items = aitams
portion = paarshan
flavour = plevar/flevar
topping = taappink/topping
crispy = krispi
crunchy = kranchi
juicy = juusi
soft = saapt/soft
fresh = presh/fresh
fried = praid/fraid
grill = kiril/grill
lemon = lemen/leman
honey = hani
pudding = putink
waffle = vaapil/vaffal
brownie = pravuni/brauni
noodles = nuutils
pasta = paasta
fries = prais/frais
popcorn = paapkaarn
home = hom
house = havus/haus
room = ruum
hall = haal
kitchen = kichan
office = aapis/aafis/opis/aaphis
college = kaalej/kalej
school = skool
class = klaas
exam = eksaam/eksam
hospital = haaspital/aaspattiri
beach = piich
park = paark
mall = maal
theatre = tiyettar/thiyetar
cinema = sinima
movie = muuvi
film = pilim/film
road = rot
bus = pas/bas
car = kaar
bike = paik/baik/baaik
auto = aatto/aato
train = tren/rayil
flight = plait/flait
airport = erpaart/eyarport
station = steshan
ticket = tikket/tikat/tiket
trip = trip
travel = traval/travel
tour = tuur
vlog = vlaak/vlog
video = vitiyo/viidiyo/vidiyo
channel = chenal/sanal
subscribe = sapskraip/sabskraib
like = laik
share = sher
comment = kaament/kament
follow = paalo/faalo
support = sappaart/saport
content = kaantent/kontent
reel = riil
reels = riils
shorts = shaarts
post = post
story = staari/stori
review = rivyuu/riviyu
link = link
description = tiskripshan/diskripshan
phone = pon/fon/phon
mobile = mopail/mobail/mobaail
camera = kemara/kemra
photo = poto/foto
selfie = selpi/selfi
laptop = leptaap/laptop
internet = intarnet
wifi = vaipai/vaifai
charge = saarj/chaarj
charger = saarjar/chaarjar
battery = petari/bettari
app = aap/ep
game = kem/gem
online = aanlain/onlain
update = aptet/apdet
download = taunlot/daunlod
account = akkavunt/akaunt
password = paasvart/pasvord
message = mesej
call = kaal
number = nampar/nambar
time = taim/taaim
minute = minit
minutes = minits
hour = havar/avar
week = viik
weekend = viikent/viikend
month = manth
morning = maarnink/morning
evening = iivnink/iivning
night = nait
monday = manti/mande
tuesday = tyuustey/chuustey
wednesday = vensti/venstey
thursday = tharsti/tharsdey
friday = praiti/fraide
saturday = settarti/saturde
sunday = santi/sande
birthday = partte/barthde
party = paarti
function = pankshan/fankshan
wedding = veddink/vedding
family = pemili/femili
team = tiim
plan = plaan/plen
idea = aitiya/aidiya
problem = praaplam/problem
issue = ishyuu/ishu
tension = tenshan
pressure = preshar
doubt = tavut/daut
chance = saans/chaans
choice = chais
option = aapshan/opshan
start = staart
stop = staap/stop
finish = pinish/finish
ready = reti/redi
wait = veyit/veit/vet
try = trai/tirai/dirai
check = chek
test = test
set = set
change = chenj/sench
use = yuus
open = open
close = klos
book = puk/buk
booking = pukkink/bukking
cancel = kensal/kansal
confirm = kaanparm/konfarm
sure = shuur
perfect = parpekt/perfekt
correct = karekt/korekt
wrong = raank/rong
right = rait
left = lept/left
straight = stret
side = sait/said
back = pek/bek
front = prant/frant
top = taap/top
line = lain
queue = kyuu
rush = rash
crowd = kravut/kraud
traffic = trapik/trafik
parking = paarkink/parking
signal = sikanal/signal
gate = ket/get
door = taar/dor
window = vintow/vindo
table = tepil/tebil
chair = cher
fan = pen/fen
light = lait
shirt = shart
pant = pent/pant
dress = tres/dres
shoes = shuus
bag = pek/beg
watch = vaach/vaatch
gift = kipt/gift
money = mani
cash = kesh
card = kaart
payment = pement
salary = sampalam/selari
job = jaap/jaab
work = vork/vark
company = kampeni/kampani
boss = paas/bas
manager = menejar
meeting = miitink/miiting
project = praajekt/projekt
interview = intarvyu/intarviyu
training = treyinink/trening
practice = praaktis/prektis
match = mech/maach
cricket = kirikket/kriket
football = putpaal/futbal
score = skor
winner = vinnar
prize = prais
challenge = chelanj/sallenj
task = taask
level = leval
round = ravunt/raund
final = painal/fainal
season = siisan
episode = episot/episod
series = siiris
part = paart
enjoy = enjaay/enjoy
feel = piil/fiil
feeling = piilink/fiiling
mood = muut/muud
vibe = vaip/vaib
happy = heppi/happi
sad = set/sad
angry = enkri/angri
excited = eksaitet/eksaited
tired = taiyart/taiard
busy = pisi/bisi
free = pri/frii
bored = paart/bord
love = lav
crush = krash
matter = mettar/matar
scene = siin
setup = setap
style = stail
fashion = peshan/feshan
look = luk
colour = kalar
color = kalar
black = plaak/blek
white = vait
blue = pluu/bluu
green = kriin/griin
pink = pink
size = sais
small = smaal
medium = miitiyam/miidiyam
large = laarj
golden = koltan/golden/koldan
silver = silvar
gold = kolt/gold
diamond = taimant/daimand
rich = rich
basic = pesik/besik
local = lokal
famous = pemas/femas
popular = paapular/popular
trend = trent/trend
trending = trentink/trending
viral = vairal
new = nyuu
old = olt/old
latest = letest
total = tottal/total
average = averej/aavarej
maximum = maaksimam/maksimam
minimum = minimam
limit = limit
piece = piis
pieces = piises
type = taip
types = taips
variety = varaiti
mix = miks
mixed = mikst
combination = kaampineshan/kombineshan
recommend = rekamant/rekamend
recommendation = rekamanteshan/rekamendeshan
suggest = sajest
opinion = opiniyan
rating = retink/reting
worth = vort/varth
value = valyu/vaalyu
three = tri/thrii
four = paar/foor
five = paiv/faiv
seven = sevan/seven
eight = eyt
nine = nain
twenty = tventi/tuventi
thirty = tharti
forty = paarti/forti
fifty = pipti/fifti/piptti
fifteen = piptin/pipteen/fifteen
sixty = siksti
hundred = hantret/handred
thousand = tavusant/thausand
"""

  /** Native words that sound like a listed English word: never changed. */
  private val NATIVE_LOOKALIKES: Map<Script, Set<String>> = mapOf(
    // pul = grass, pon/pona = went, paal = milk, kaal = leg, mani = bell/hour, kek = ask,
    // pen = woman, kaar = car or rain cloud, set = a set or ready
    Script.TAMIL to setOf("pul", "pon", "pona", "ponen", "paal", "kaal", "mani", "kek", "pen", "piil", "kem", "kaar", "set", "kalar", "nain"),
    Script.MALAYALAM to setOf("pul", "paal", "kaal", "mani", "pen", "kaar", "nain", "pon"),
    Script.TELUGU to setOf("pul", "paalu", "kaalu", "mani", "pen", "kaar", "pon"),
    Script.KANNADA to setOf("pul", "haalu", "kaalu", "mani", "pen", "kaar", "pon"),
    Script.DEVANAGARI to setOf("pal", "kal", "mani", "pen", "kaar", "set", "tab", "sab", "aap", "ret", "hai", "hal", "lain", "laal", "maal", "ek", "nai", "pani", "baat"),
  )

  /** Endings that Dravidian languages add straight onto a word ("normal-aa", "bike-ku"). */
  private val ENDINGS: Map<Script, List<String>> = mapOf(
    Script.TAMIL to listOf("ukkaaga", "kkaaga", "ukku", "kku", "ulla", "ula", "ile", "laye", "le", "la", "oda", "ode", "odu", "yum", "um", "dhaan", "thaan", "aave", "aa", "ku", "nu", "naa"),
    Script.MALAYALAM to listOf("ilekku", "inte", "ude", "il", "inu", "um", "aanu"),
    Script.TELUGU to listOf("lone", "lo", "ki", "ni", "tho", "gaa", "ga", "loo"),
    Script.KANNADA to listOf("nalli", "alli", "ge", "inda"),
  )

  private class Entry(val english: String, val key: String)

  private val bySkeleton: Map<String, List<Entry>> by lazy {
    val m = HashMap<String, MutableList<Entry>>()
    for (raw in LIST.lines()) {
      val line = raw.trim()
      if (line.isEmpty() || '=' !in line) continue
      val english = line.substringBefore('=').trim()
      for (s in line.substringAfter('=').split('/').map { it.trim() }.distinct()) {
        val k = key(s)
        if (skeleton(k).isEmpty()) continue
        m.getOrPut(skeleton(k)) { ArrayList() }.add(Entry(english, k))
      }
    }
    m
  }

  /**
   * The English spelling for [heard] (one word in plain letters, from [script] text), with any
   * native ending kept apart ("normal aa"), or null when it is not a listed English word.
   */
  fun spell(heard: String, script: Script): String? {
    val w = heard.lowercase()
    if (w.length < 2 || w.any { it !in 'a'..'z' }) return null
    if (w in NATIVE_LOOKALIKES[script].orEmpty()) return null
    for (ending in ENDINGS[script].orEmpty()) {
      if (w.length - ending.length < 3 || !w.endsWith(ending)) continue
      val stem = w.dropLast(ending.length)
      // The stem must be a real word, not one syllable ("pai-kku" is not "bye ku").
      if (stem in NATIVE_LOOKALIKES[script].orEmpty() || skeleton(key(stem)).length < 2) continue
      match(stem)?.let { return "$it $ending" }
    }
    return match(w)
  }

  private fun match(w: String): String? {
    val k = key(w)
    val sk = skeleton(k)
    if (sk.isEmpty()) return null
    val candidates = bySkeleton[sk] ?: return null
    // Short words must sound exactly alike; longer ones may differ a little in their vowels.
    val allowed = when {
      sk.length <= 2 -> 0.0
      sk.length == 3 -> 0.5
      sk.length == 4 -> 1.0
      else -> 1.5
    }
    var best: Entry? = null
    var bestD = Double.MAX_VALUE
    for (c in candidates) {
      val d = vowelDifference(k, c.key) ?: continue
      if (d < bestD) {
        bestD = d
        best = c
      }
    }
    return if (best != null && bestD <= allowed) best.english else null
  }

  private const val VOWELS = "aeiou"

  /**
   * A rough sound key: voiced and plain stops alike, f like p, w like v, all hissing sounds
   * alike, no h or y, long vowels short, doubled letters single, and the vowels Indian scripts
   * add to English clusters ("pirend", "iskool", "bassu") dropped.
   */
  internal fun key(word: String): String {
    var s = word.lowercase().filter { it in 'a'..'z' }
    s = s.replace("zh", "l").replace("th", "t").replace("dh", "d").replace("sh", "s").replace("ch", "s")
      .replace("ph", "p").replace("kh", "k").replace("gh", "k").replace("bh", "b").replace("jh", "j")
      .replace("ck", "k").replace("x", "ks").replace("qu", "kv")
    val sb = StringBuilder(s.length)
    for (c in s) {
      sb.append(
        when (c) {
          'b', 'f' -> 'p'
          'd' -> 't'
          'g', 'c', 'q' -> 'k'
          'j', 'z' -> 's'
          'w' -> 'v'
          'h' -> continue
          // A "y" glide between vowels ("preemiyam") is not a sound of its own; after a consonant it is ("pyaar").
          'y' -> if (sb.isNotEmpty() && sb.last() in VOWELS) continue else 'y'
          else -> c
        },
      )
    }
    // One spelling per long vowel, and doubled consonants single.
    var k = sb.toString().replace("ii", "ee").replace("uu", "oo")
    k = k.fold(StringBuilder()) { acc, c -> if (acc.isNotEmpty() && acc.last() == c && c !in VOWELS) acc else acc.append(c) }.toString()
    // A vowel slipped into a starting cluster: "pirend" -> "prend", "tirai" -> "trai".
    if (k.length >= 4 && k[0] !in VOWELS && (k[1] == 'i' || k[1] == 'u') && (k[2] == 'r' || k[2] == 'l') && k[3] in VOWELS) {
      k = k.substring(0, 1) + k.substring(2)
    }
    // A vowel put before an "s" cluster: "iskool" -> "skool".
    if (k.length >= 4 && k[0] == 'i' && k[1] == 's' && k[2] !in VOWELS) k = k.substring(1)
    // The short "u" that Tamil, Malayalam and Telugu add after a final consonant: "bassu".
    if (k.length >= 3 && k.last() == 'u' && k[k.length - 2] !in VOWELS) k = k.dropLast(1)
    return k
  }

  private fun skeleton(k: String): String = k.filter { it !in VOWELS }

  /** Consonants, with each run of vowels as one piece: "taaim" -> t, aai, m. */
  private fun shape(k: String): List<String> {
    val out = ArrayList<String>()
    var i = 0
    while (i < k.length) {
      if (k[i] in VOWELS) {
        var j = i
        while (j < k.length && k[j] in VOWELS) j++
        out.add(k.substring(i, j))
        i = j
      } else {
        out.add(k[i].toString())
        i++
      }
    }
    return out
  }

  /**
   * How far apart two keys sound, when they have the same consonants and the same syllables
   * (null otherwise): 0.5 for a vowel said longer or shorter, 1 for a different vowel.
   */
  private fun vowelDifference(a: String, b: String): Double? {
    val sa = shape(a)
    val sb = shape(b)
    if (sa.size != sb.size) return null
    var d = 0.0
    for (i in sa.indices) {
      val x = sa[i]
      val y = sb[i]
      if (x == y) continue
      if (x[0] !in VOWELS || y[0] !in VOWELS) return null
      d += if (short(x) == short(y)) 0.5 else 1.0
    }
    return d
  }

  private fun short(v: String): String =
    v.replace("aa", "a").replace("ee", "i").replace("oo", "u").fold(StringBuilder()) { acc, c -> if (acc.isNotEmpty() && acc.last() == c) acc else acc.append(c) }.toString()
}
