#!/usr/bin/env bash
# 同梱タイル(level 1 = 4096×2048)から正射投影の地球儀にオレンジの足跡を添えたアプリアイコンを生成する。
# 依存: ffmpeg, python3(標準ライブラリのみ)。使い方: make_icon.sh <assets/tiles> <res ディレクトリ> [<docs/play ディレクトリ>]
set -euo pipefail
TILES="${1:?tiles ディレクトリ}"; RES="${2:?res ディレクトリ}"; PLAY="${3:-}"; TMP=$(mktemp -d)
# level 1 のタイル 4×2 を結合して 4096×2048 の正距円筒図にする
ffmpeg -v error -y -i "$TILES/1/0_0.jpg" -i "$TILES/1/1_0.jpg" -i "$TILES/1/2_0.jpg" -i "$TILES/1/3_0.jpg" \
  -i "$TILES/1/0_1.jpg" -i "$TILES/1/1_1.jpg" -i "$TILES/1/2_1.jpg" -i "$TILES/1/3_1.jpg" \
  -filter_complex "[0][1][2][3]hstack=4[t];[4][5][6][7]hstack=4[b];[t][b]vstack=2,format=rgb24" -f rawvideo "$TMP/src.rgb"
python3 - "$TMP" <<'PY'
import math,sys
T=sys.argv[1]; SW,SH=4096,2048
src=open(f'{T}/src.rgb','rb').read()
def s(lon,lat):
    u=(lon+math.pi)/(2*math.pi); v=(math.pi/2-lat)/math.pi
    x=min(SW-1,max(0,int(u*SW))); y=min(SH-1,max(0,int(v*SH))); i=(y*SW+x)*3
    return src[i],src[i+1],src[i+2]
N=720; R=N/2; lat0=math.radians(5); lon0=math.radians(35)   # アフリカ〜インド洋を正面に
o=bytearray(N*N*4)
for py in range(N):
    for px in range(N):
        dx=(px-R+.5)/R; dy=-(py-R+.5)/R; rho=math.hypot(dx,dy); idx=(py*N+px)*4
        if rho>1: o[idx:idx+4]=bytes((0,0,0,0)); continue
        c=math.asin(min(1,rho)) if rho>0 else 0; sc=math.sin(c); cc=math.cos(c)
        if rho==0: lat,lon=lat0,lon0
        else:
            lat=math.asin(cc*math.sin(lat0)+dy*sc*math.cos(lat0)/rho)
            lon=lon0+math.atan2(dx*sc, rho*math.cos(lat0)*cc-dy*math.sin(lat0)*sc)
        r,g,b=s(lon,lat); sh=.60+.40*cc; a=255
        if rho>.985: a=int(255*(1-(rho-.985)/.015))
        o[idx]=min(255,int(r*sh)); o[idx+1]=min(255,int(g*sh)); o[idx+2]=min(255,int(b*sh)); o[idx+3]=max(0,a)
# 足跡(肉球 1 + 指 4)を右下に描く。縁取りの濃色 → オレンジ の順に重ねる
def disc(cx,cy,rad,col,ry=None):
    ry=ry or rad
    for py in range(max(0,int(cy-ry-2)),min(N,int(cy+ry+3))):
        for px in range(max(0,int(cx-rad-2)),min(N,int(cx+rad+3))):
            d=math.hypot((px+.5-cx)/rad,(py+.5-cy)/ry)
            if d<=1:
                aa=min(1,(1-d)*rad*1.5)
                idx=(py*N+px)*4
                for k in range(3): o[idx+k]=int(o[idx+k]*(1-aa)+col[k]*aa)
                o[idx+3]=255
def paw(cx,cy,S,col):
    disc(cx,cy+S*.28,S*.56,col,S*.46)                       # 肉球
    for tx,ty,rr in ((-.66,-.10,.21),(-.24,-.40,.23),(.24,-.40,.23),(.66,-.10,.21)):   # 指 4 本
        disc(cx+tx*S,cy+ty*S,S*rr,col)
paw(N*.735,N*.735,100,(20,14,10))
paw(N*.735,N*.735,88,(255,138,101))
open(f'{T}/globe.rgba','wb').write(bytes(o))
PY
ffmpeg -v error -y -f rawvideo -pix_fmt rgba -s 720x720 -i "$TMP/globe.rgba" "$TMP/globe.png"
# foreground(地球中央), background(宇宙・緑がかった深い色), legacy(合成)
ffmpeg -v error -y -i "$TMP/globe.png" -vf "pad=1024:1024:152:152:color=0x00000000" "$TMP/fg.png"
ffmpeg -v error -y -f lavfi -i "color=0x061410:s=1024x1024" -vf "geq=r='clip(6+14*(1-hypot(X-512,Y-440)/720),4,30)':g='clip(20+34*(1-hypot(X-512,Y-440)/720),10,70)':b='clip(16+26*(1-hypot(X-512,Y-440)/620),10,60)'" -frames:v 1 "$TMP/bg.png"
ffmpeg -v error -y -i "$TMP/bg.png" -i "$TMP/globe.png" -filter_complex "[0][1]overlay=152:152" "$TMP/legacy.png"
declare -A D=( [mdpi]=48 [hdpi]=72 [xhdpi]=96 [xxhdpi]=144 [xxxhdpi]=192 )
for d in "${!D[@]}"; do mkdir -p "$RES/mipmap-$d"; ffmpeg -v error -y -i "$TMP/legacy.png" -vf "scale=${D[$d]}:${D[$d]}:flags=lanczos" "$RES/mipmap-$d/ic_launcher.png"; cp "$RES/mipmap-$d/ic_launcher.png" "$RES/mipmap-$d/ic_launcher_round.png"; done
mkdir -p "$RES/drawable"
ffmpeg -v error -y -i "$TMP/fg.png" -vf scale=432:432:flags=lanczos "$RES/drawable/ic_launcher_globe_fg.png"
ffmpeg -v error -y -i "$TMP/bg.png" -vf scale=432:432:flags=lanczos "$RES/drawable/ic_launcher_globe_bg.png"
if [ -n "$PLAY" ]; then
  mkdir -p "$PLAY"
  ffmpeg -v error -y -i "$TMP/legacy.png" -vf "scale=512:512:flags=lanczos" "$PLAY/icon_512.png"
  # フィーチャーグラフィック 1024×500: 左に地球儀、右にアプリ名(FONT に日本語フォントのパスを渡す。なければ文字なし)
  FONT="${FONT:-$(fc-list :lang=ja 2>/dev/null | head -1 | cut -d: -f1)}"
  TXT=""
  if [ -n "$FONT" ]; then
    TXT=",drawtext=fontfile='$FONT':text='地球儀で見る':x=470:y=112:fontsize=50:fontcolor=white"
    TXT="$TXT,drawtext=fontfile='$FONT':text='絶滅危惧種生物図鑑':x=470:y=178:fontsize=60:fontcolor=white"
    TXT="$TXT,drawtext=fontfile='$FONT':text='世界の絶滅危惧種 1,063 種を':x=472:y=295:fontsize=30:fontcolor=0xA5D6A7"
    TXT="$TXT,drawtext=fontfile='$FONT':text='写真と解説つきで、オフラインでも':x=472:y=340:fontsize=30:fontcolor=0xA5D6A7"
  fi
  ffmpeg -v error -y -f lavfi -i "color=0x061410:s=1024x500" -i "$TMP/globe.png" -filter_complex "[1]scale=420:420[g];[0][g]overlay=30:40$TXT" -frames:v 1 "$PLAY/feature_1024x500.png"
fi
rm -rf "$TMP"; echo "icon generated into $RES"
