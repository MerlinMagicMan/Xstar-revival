from xstar_protocol.diff import compare_bytes


def test_compare_detects_new_message_and_video_signature():
    left = bytes([0xFE, 0, 1, 1, 1, 0, 0, 0])
    right = left + bytes([0xFE, 0, 2, 1, 1, 30, 0, 0]) + b"\x00\x00\x00\x01\x67x"
    result = compare_bytes(left, right)
    assert result["mavlink_count_delta"] == 1
    assert result["message_id_delta"] == {30: 1}
    assert result["new_signature_kinds"] == ["h264"]
