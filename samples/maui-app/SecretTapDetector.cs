namespace DynamicMauiDemo;

/// <summary>Reveals developer tools after repeated taps within a time limit.</summary>
public static class SecretTapDetector
{
    public static void Attach(View view, Action onActivate, int tapsRequired = 7, TimeSpan? resetAfter = null)
    {
        var reset = resetAfter ?? TimeSpan.FromSeconds(2);
        var taps = 0;
        DateTime? lastTap = null;

        var recognizer = new TapGestureRecognizer();
        recognizer.Tapped += (_, _) =>
        {
            var now = DateTime.UtcNow;
            if (lastTap is null || now - lastTap > reset) taps = 0;
            lastTap = now;
            taps++;

            if (taps >= tapsRequired)
            {
                taps = 0;
                lastTap = null;
                onActivate();
            }
        };

        view.GestureRecognizers.Add(recognizer);
    }
}
