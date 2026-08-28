from xstar_protocol.signatures import scan_signatures


def test_detects_h264_and_rtsp():
    data = b"xxxxRTSP/1.0 200 OK\r\n" + b"\x00\x00\x00\x01\x67abc"
    hits = scan_signatures(data)
    assert [h.kind for h in hits] == ["rtsp", "h264"]


def test_detects_http_request():
    hits = scan_signatures(b"noiseGET /camera HTTP/1.1\r\n")
    assert any(h.kind == "http" for h in hits)
