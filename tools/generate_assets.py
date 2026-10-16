from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter
import math

OUT = Path("resources/sonic")
OUT.mkdir(parents=True, exist_ok=True)
S = 4
W = H = 192


def sc(v):
    if isinstance(v, (tuple, list)):
        return tuple(int(x * S) for x in v)
    return int(v * S)


def canvas():
    return Image.new("RGBA", (W*S, H*S), (0,0,0,0))


def poly(draw, pts, fill, outline=None, width=1):
    pts = [sc(p) for p in pts]
    draw.polygon(pts, fill=fill)
    if outline:
        draw.line(pts + [pts[0]], fill=outline, width=sc(width), joint="curve")


def ellipse(draw, box, fill, outline=None, width=1):
    draw.ellipse(sc(box), fill=fill, outline=outline, width=sc(width) if outline else 1)


def rounded(draw, box, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(sc(box), radius=sc(radius), fill=fill, outline=outline, width=sc(width) if outline else 1)


def glow_layer(base, mask_source, color, radius, alpha):
    alpha_mask = mask_source.getchannel("A").filter(ImageFilter.GaussianBlur(sc(radius)))
    tint = Image.new("RGBA", base.size, (*color, 0))
    tint.putalpha(alpha_mask.point(lambda p: int(p * alpha / 255)))
    base.alpha_composite(tint)


def finish(im, name):
    im = im.resize((W, H), Image.Resampling.LANCZOS)
    # Force conventional non-interlaced RGBA PNG output for Geode's decoder.
    im.save(OUT / name, format="PNG", optimize=False, compress_level=6)


def draw_aura(im, gold=True):
    aura = Image.new("RGBA", im.size, (0,0,0,0))
    d = ImageDraw.Draw(aura)
    cx, cy = sc(96), sc(97)
    col = (255, 205, 20, 85) if gold else (30, 155, 255, 55)
    for r, a in [(72,25),(60,35),(50,45)]:
        d.ellipse((cx-sc(r), cy-sc(r), cx+sc(r), cy+sc(r)), outline=(*col[:3], a), width=sc(3))
    for i in range(12):
        a = i * math.tau / 12
        r1, r2 = 58, 80 + (i%3)*5
        p1=(96+math.cos(a)*r1,97+math.sin(a)*r1)
        p2=(96+math.cos(a-.07)*r2,97+math.sin(a-.07)*r2)
        p3=(96+math.cos(a+.07)*r2,97+math.sin(a+.07)*r2)
        poly(d,[p1,p2,p3],(*col[:3],32))
    aura = aura.filter(ImageFilter.GaussianBlur(sc(1.5)))
    im.alpha_composite(aura)


def sonic_idle(super_form=False):
    im = canvas()
    if super_form:
        draw_aura(im, True)
    d = ImageDraw.Draw(im)
    # ground shadow
    ellipse(d,(50,164,145,180),(0,0,0,70))

    if super_form:
        main=(255,211,21,255); dark=(220,134,0,255); light=(255,248,107,255)
        # tall swept-up super spines
        spines=[[(67,105),(38,66),(72,80)],[(72,85),(58,32),(91,67)],[(83,72),(88,20),(108,66)],[(101,70),(123,27),(119,83)],[(112,82),(148,52),(127,102)]]
    else:
        main=(23,102,230,255); dark=(8,48,151,255); light=(52,174,255,255)
        spines=[[(72,84),(37,57),(62,92)],[(65,99),(25,89),(65,112)],[(68,114),(31,124),(77,127)],[(86,71),(65,35),(98,62)]]

    # dark silhouette edge behind body
    for pts in spines:
        poly(d,[(x+2,y+3) for x,y in pts],(13,20,45,235))
    for pts in spines:
        poly(d,pts,main,dark,2)

    # body/torso
    ellipse(d,(70,92,124,151),main,dark,2)
    # belly patch
    ellipse(d,(88,115,118,149),(244,188,119,255),(182,106,66,255),1)

    # head
    ellipse(d,(66,60,128,119),main,dark,2)
    # forehead highlight
    poly(d,[(82,68),(99,59),(112,66),(96,70)],light)

    # ear
    poly(d,[(78,67),(83,47),(96,68)],main,dark,2)
    poly(d,[(82,64),(85,53),(91,66)],(242,161,148,255))

    # face muzzle
    ellipse(d,(93,83,137,116),(244,188,119,255),(155,89,55,255),1)
    # eye white (single Sonic-style eye shape)
    rounded(d,(91,69,124,99),13,(250,252,255,255),(20,25,35,255),1)
    ellipse(d,(109,75,119,94),(34,52,67,255))
    ellipse(d,(112,77,116,82),(255,255,255,255))
    # nose
    ellipse(d,(130,93,143,104),(12,16,22,255))
    ellipse(d,(133,94,137,97),(255,255,255,180))
    # smile
    d.arc(sc((109,96,130,113)),start=12,end=90,fill=(75,39,31,255),width=sc(1))

    # back arm
    d.line([sc((78,116)),sc((58,135))], fill=dark, width=sc(9))
    ellipse(d,(49,127,67,145),(245,247,250,255),(80,80,90,255),1)
    # forward arm / glove
    d.line([sc((115,119)),sc((137,132))], fill=dark, width=sc(9))
    ellipse(d,(133,125,153,146),(250,250,251,255),(80,80,90,255),1)
    # glove finger hints
    d.line([sc((140,132)),sc((151,137))], fill=(180,180,185,255), width=sc(1))

    # legs
    d.line([sc((91,145)),sc((82,163))], fill=dark, width=sc(9))
    d.line([sc((111,145)),sc((119,163))], fill=dark, width=sc(9))
    # shoes
    rounded(d,(61,155,93,174),8,(225,38,42,255),(80,20,25,255),2)
    rounded(d,(108,155,142,174),8,(225,38,42,255),(80,20,25,255),2)
    rounded(d,(65,157,89,164),3,(250,250,250,255))
    rounded(d,(111,157,137,164),3,(250,250,250,255))
    # shoe shine
    d.line([sc((70,167)),sc((85,167))], fill=(255,112,95,255), width=sc(2))
    d.line([sc((116,167)),sc((132,167))], fill=(255,112,95,255), width=sc(2))

    # subtle rim light
    d.arc(sc((67,61,128,119)),180,300,fill=light,width=sc(2))

    # extra aura glow after figure for Super
    if super_form:
        glow = im.copy()
        alpha=glow.getchannel('A').filter(ImageFilter.GaussianBlur(sc(5)))
        tint=Image.new('RGBA',im.size,(255,196,0,0)); tint.putalpha(alpha.point(lambda p:int(p*.20)))
        im.alpha_composite(tint)
    return im


def spin_ball(super_form=False):
    im=canvas()
    if super_form:
        draw_aura(im, True)
    d=ImageDraw.Draw(im)
    cx,cy=96,100
    if super_form:
        main=(255,213,20,255); dark=(207,118,0,255); light=(255,250,128,255)
    else:
        main=(20,108,237,255); dark=(6,46,145,255); light=(71,192,255,255)
    # motion streaks
    for dy,a in [(-20,70),(0,100),(20,55)]:
        d.line([sc((28,cy+dy)),sc((68,cy+dy))],fill=(*light[:3],a),width=sc(4))
    # spikes around ball
    for i in range(12):
        a=i*math.tau/12
        r1=43; r2=55 if i%2==0 else 50
        pts=[(cx+math.cos(a-.16)*r1,cy+math.sin(a-.16)*r1),(cx+math.cos(a)*r2,cy+math.sin(a)*r2),(cx+math.cos(a+.16)*r1,cy+math.sin(a+.16)*r1)]
        poly(d,pts,main,dark,1)
    ellipse(d,(52,56,140,144),main,dark,3)
    # swirl bands
    d.arc(sc((58,62,134,138)),195,345,fill=light,width=sc(7))
    d.arc(sc((61,58,137,134)),15,165,fill=dark,width=sc(6))
    d.arc(sc((68,70,126,128)),200,350,fill=(255,255,255,155),width=sc(3))
    # curled glove and shoe flashes
    ellipse(d,(85,72,103,90),(248,248,250,255),(90,90,95,255),1)
    rounded(d,(97,113,126,128),6,(224,40,42,255),(80,20,25,255),1)
    # center dark accent gives depth
    ellipse(d,(80,82,113,116),(*dark[:3],90))
    return im


def emerald():
    im=canvas(); d=ImageDraw.Draw(im)
    # grayscale-white gem so C++ tinting preserves color
    shadow=Image.new('RGBA',im.size,(0,0,0,0)); sd=ImageDraw.Draw(shadow)
    poly(sd,[(96,35),(151,66),(133,126),(96,160),(59,126),(41,66)],(255,255,255,130))
    shadow=shadow.filter(ImageFilter.GaussianBlur(sc(5)))
    im.alpha_composite(shadow)
    d=ImageDraw.Draw(im)
    poly(d,[(96,31),(150,64),(132,124),(96,158),(60,124),(42,64)],(225,225,235,255),(255,255,255,255),2)
    poly(d,[(96,31),(116,65),(76,65)],(255,255,255,255))
    poly(d,[(42,64),(76,65),(60,124)],(174,174,190,255))
    poly(d,[(150,64),(116,65),(132,124)],(205,205,218,255))
    poly(d,[(76,65),(116,65),(96,158)],(245,245,250,255))
    d.line([sc((61,68)),sc((131,68))],fill=(255,255,255,255),width=sc(2))
    return im


def ring():
    im=canvas()
    glow=Image.new('RGBA',im.size,(0,0,0,0)); gd=ImageDraw.Draw(glow)
    gd.ellipse(sc((43,43,149,149)),outline=(255,220,45,150),width=sc(13))
    glow=glow.filter(ImageFilter.GaussianBlur(sc(6))); im.alpha_composite(glow)
    d=ImageDraw.Draw(im)
    d.ellipse(sc((48,48,144,144)),outline=(242,174,8,255),width=sc(13))
    d.ellipse(sc((52,52,140,140)),outline=(255,235,85,255),width=sc(6))
    d.arc(sc((50,50,142,142)),205,322,fill=(255,255,205,255),width=sc(4))
    return im


finish(sonic_idle(False), "sonic_idle.png")
finish(spin_ball(False), "sonic_spin.png")
finish(sonic_idle(True), "super_idle.png")
finish(spin_ball(True), "super_spin.png")
finish(emerald(), "emerald.png")
finish(ring(), "ring.png")

# Sanity check files by reopening them before the Geode build step.
for p in OUT.glob("*.png"):
    with Image.open(p) as chk:
        chk.load()
        assert chk.mode == "RGBA"
print("Generated HD Sonic assets:")
for p in sorted(OUT.glob("*.png")):
    print(p, p.stat().st_size)
