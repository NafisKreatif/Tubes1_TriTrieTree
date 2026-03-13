const richard = new Image();

richard.onload = async () => {
    const paul = document.createElement('canvas');
    paul.width = richard.width;
    paul.height = richard.height;
    document.body.appendChild(paul);
    const astley = paul.getContext('2d');
    astley.drawImage(richard, 0, 0);
    const oof = astley.getImageData(0, 0, paul.width, paul.height);
    const guh = new Array(oof.height).fill(0).map(() => new Array(oof.width));
    for (let i = 0; i < oof.data.length; i += 4) {
        let x = ~~(i / 4) % oof.width;
        let y = ~~((i / 4) / oof.width);
        guh[y][x] = `{ ${oof.data[i]}, ${oof.data[i + 1]}, ${oof.data[i + 2]} }`;
    }
    const str = '{\n    { ' + guh.reverse().map((r) => r.join(', ')).join(' },\n            { ') + ' }\n    }';
    const a = document.createElement('a');
    a.onclick = () => navigator.clipboard.writeText(str);
    a.innerText = 'CLIcK ME!!!!';
    document.body.appendChild(a);
};

richard.src = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABQAAAAUCAIAAAAC64paAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAAPOSURBVDhPFVNJbxxFFK61q7p7PD1j4yy2ZSRn4cKiIBbBhQso94ifh4RyJheCBJIRgQgJCUJEiJKQEOwQOx5v2DPTW+1VPPeppH7L974Ff3nz/sLCQKtGmw6l5ExLSPDwGb08HgRnfaAhpbqZCpEzXhS5TBH56E2wRJYyxECFGC0tXbi4LDgnCOU5HxaR7t3RT78makIojEWyKEbj0fi15UG1KMshIpT0RjWmFaWw1s7njcwFwpgSnE82/7hz+8/7vz2+c1P1cy5kr6x2gWXSOc8yEWIiC8MxpayZtyLLGIFxNC/y0XDQnDYbl6598sH1jdU3iiJLMZZloft6b39SDAYnpyejxWVCKPbBMo58cDLPvdWAkSBcoNJq03ZNsbiSlVVKwdiOcu6d3dt/wTIiBwWZzU7zPDfaVAsLUkjBM2PUyXzKsoojJGnGyyVV94SQ4WCYMZjsRA6YfSkLkkFJtARFBbfbPiGfZSTDRFUXE0KYUFWNheQhGO+tzLnIaLCGE1ZPZ0RIBgL5oI8P973tTddgZ+eHz7GdMpwBB3v/PsNeSU5U1zXTGaccCD1/7nwhOf30+g1nFWwDqTmJLx58f+/nW1vPfneyrGhmFte+u7u5/ewejWZ1bd2FgAhp+h5hlBeSCIEZDSDvsBocv/z77t3bzyc7h/UspniQl0/2D0KwH6693u5u3/rqC4xSDG5l5ULXzCeTPeIcwKmNnUejjl/9o6MLKbbW6hg7pR49/qXWtTH9qKxO5qfO9GcMGZ2X5XBcEaN1NRr1dZNLMZk8N9GC9WKMVuuj/w4suDC4h3vbtWsAsxSZbhoQkmLcwoNxihGilATVAiPQ52MY5fn09BhKWtXDrCf7L+9t/cUIm82PiizjmAyKAlwInkKIJMbJrz9+09TH5wbDaxfXb7z53sbi8snpkY82wSEoTfuZseqHn75tu1PMiJQyo5ycrfKOICaEUFpZF+p2vnWwe1hPI0Y+eZ8CxhiCZk0z2d999PRB37ZNV2vTg0nA1DIicuWdj65ceXd99fKrut3cenhiWwMhRZCnlIBllIyrCU5rqxsxGKU6cCvregXRzTAtqqWPP/vcttOdo13dRgd4nCUYozOrIC6Lq5cuX924trK6rpzyXjsfie17rXUIZwxDaH1CgkuKaJ4PCS0YH0eM4Q9lhZTLb7/1PpjkDA3kESdiHYjVWat61bZaOQCIKWcZY6IaXeAsp0xA9MDb2zvbkGi4H/qBaMooSSim4DrVNqqxyfOMU5JB3EG/amEIOwSvKC5Rws64nd2XUA/tjBBO8f/r6GqXsLEbDwAAAABJRU5ErkJggg==';
