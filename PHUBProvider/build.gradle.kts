// use an integer for version numbers
version = 1


cloudstream {
    language = "es"
    // All of these properties are optional, you can safely remove them

    // description = "Lorem Ipsum"
    // authors = listOf("Cloudburst")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(

        "TvSeries",
        "Movie",
    )

    iconUrl = "https://image.winudf.com/v2/image1/Y29tLm5lYXIucGhfaWNvbl8xNjU1NTExNzAxXzA5NA/icon.png?w=100&fakeurl=1&type=.webp"
}