package tutorials.AudioChannelManipulations;

import com.bitmovin.api.sdk.BitmovinApi;
import com.bitmovin.api.sdk.common.BitmovinException;
import com.bitmovin.api.sdk.model.AacAudioConfiguration;
import com.bitmovin.api.sdk.model.AclEntry;
import com.bitmovin.api.sdk.model.AclPermission;
import com.bitmovin.api.sdk.model.AudioMixChannelType;
import com.bitmovin.api.sdk.model.AudioMixInputChannelLayout;
import com.bitmovin.api.sdk.model.AudioMixInputStream;
import com.bitmovin.api.sdk.model.AudioMixInputStreamChannel;
import com.bitmovin.api.sdk.model.AudioMixInputStreamSourceChannel;
import com.bitmovin.api.sdk.model.AudioMixSourceChannelType;
import com.bitmovin.api.sdk.model.CodecConfiguration;
import com.bitmovin.api.sdk.model.Encoding;
import com.bitmovin.api.sdk.model.EncodingOutput;
import com.bitmovin.api.sdk.model.H264VideoConfiguration;
import com.bitmovin.api.sdk.model.HttpInput;
import com.bitmovin.api.sdk.model.IngestInputStream;
import com.bitmovin.api.sdk.model.Input;
import com.bitmovin.api.sdk.model.InputStream;
import com.bitmovin.api.sdk.model.MessageType;
import com.bitmovin.api.sdk.model.Mp4Muxing;
import com.bitmovin.api.sdk.model.MuxingStream;
import com.bitmovin.api.sdk.model.Output;
import com.bitmovin.api.sdk.model.PresetConfiguration;
import com.bitmovin.api.sdk.model.S3Output;
import com.bitmovin.api.sdk.model.StartEncodingRequest;
import com.bitmovin.api.sdk.model.Status;
import com.bitmovin.api.sdk.model.Stream;
import com.bitmovin.api.sdk.model.StreamInput;
import com.bitmovin.api.sdk.model.StreamMode;
import com.bitmovin.api.sdk.model.StreamSelectionMode;
import com.bitmovin.api.sdk.model.Task;
import common.ConfigProvider;
import feign.Logger.Level;
import feign.slf4j.Slf4jLogger;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This example demonstrates how to swap 2 audio channels.
 *
 * <p>The following configuration parameters are expected:
 *
 * <ul>
 *   <li>BITMOVIN_API_KEY - Your API key for the Bitmovin API
 *   <li>BITMOVIN_TENANT_ORG_ID - (optional) The ID of the Organisation in which you want to perform
 *       the encoding.
 *   <li>HTTP_INPUT_HOST - The Hostname or IP address of the HTTP server hosting your input files,
 *       e.g.: my-storage.biz
 *   <li>INPUT_FILE_1TRACK_2CHANNELS - the path to a file containing a video with a single audio
 *       stereo stream
 *   <li>S3_OUTPUT_BUCKET_NAME - The name of your S3 output bucket. Example: my-bucket-name
 *   <li>S3_OUTPUT_ACCESS_KEY - The access key of your S3 output bucket
 *   <li>S3_OUTPUT_SECRET_KEY - The secret key of your S3 output bucket
 *   <li>S3_OUTPUT_BASE_PATH - The base path on your S3 output bucket where content will be written.
 *       Example: /outputs
 * </ul>
 *
 * <p>Configuration parameters will be retrieved from these sources in the listed order:
 *
 * <ol>
 *   <li>command line arguments (eg BITMOVIN_API_KEY=xyz)
 *   <li>properties file located in the root folder of the JAVA examples at ./examples.properties
 *       (see examples.properties.template as reference)
 *   <li>environment variables
 *   <li>properties file located in the home folder at ~/.bitmovin/examples.properties (see
 *       examples.properties.template as reference)
 * </ol>
 */
public class AudioChannelManipulation_3_ChannelSwapping {

  private static final Logger logger =
      LoggerFactory.getLogger(AudioChannelManipulation_3_ChannelSwapping.class);

  private static BitmovinApi bitmovinApi;
  private static ConfigProvider configProvider;

  public static void main(String[] args) throws Exception {
    configProvider = new ConfigProvider(args);
    bitmovinApi =
        BitmovinApi.builder()
            .withApiKey(configProvider.getBitmovinApiKey())
            // uncomment the following line if you are working with a multi-tenant account
            // .withTenantOrgId(configProvider.getBitmovinTenantOrgId())
            .withLogger(
                new Slf4jLogger(), Level.BASIC) // set the logger and log level for the API client
            .build();

    Encoding encoding = createEncoding("Audio Mapping - Example 3", "Swapping stereo channels");

    HttpInput input = createHttpInput(configProvider.getHttpInputHost());
    Output output =
        createS3Output(
            configProvider.getS3OutputBucketName(),
            configProvider.getS3OutputAccessKey(),
            configProvider.getS3OutputSecretKey());

    H264VideoConfiguration h264Config = createH264VideoConfig();
    AacAudioConfiguration aacConfig = createAacStereoAudioConfig();

    String inputFilePath = configProvider.getParameterByKey("INPUT_FILE_1TRACK_2CHANNELS");
    IngestInputStream videoIngestInputStream =
        createIngestInputStream(encoding, input, inputFilePath);
    IngestInputStream audioIngestInputStream =
        createIngestInputStream(encoding, input, inputFilePath);

    AudioMixInputStream audioMixInputStream = new AudioMixInputStream();
    audioMixInputStream.setName("Swapping channels 0 and 1");
    audioMixInputStream.setChannelLayout(AudioMixInputChannelLayout.CL_STEREO);

    AudioMixInputStreamSourceChannel sourceChannel0 = new AudioMixInputStreamSourceChannel();
    sourceChannel0.setType(AudioMixSourceChannelType.CHANNEL_NUMBER);
    sourceChannel0.setChannelNumber(0);

    AudioMixInputStreamSourceChannel sourceChannel1 = new AudioMixInputStreamSourceChannel();
    sourceChannel1.setType(AudioMixSourceChannelType.CHANNEL_NUMBER);
    sourceChannel1.setChannelNumber(1);

    AudioMixInputStreamChannel outputChannel0 = new AudioMixInputStreamChannel();
    outputChannel0.setInputStreamId(audioIngestInputStream.getId());
    outputChannel0.setOutputChannelType(AudioMixChannelType.CHANNEL_NUMBER);
    outputChannel0.setOutputChannelNumber(0);
    outputChannel0.addSourceChannelsItem(sourceChannel1);

    AudioMixInputStreamChannel outputChannel1 = new AudioMixInputStreamChannel();
    outputChannel1.setInputStreamId(audioIngestInputStream.getId());
    outputChannel1.setOutputChannelType(AudioMixChannelType.CHANNEL_NUMBER);
    outputChannel1.setOutputChannelNumber(1);
    outputChannel1.addSourceChannelsItem(sourceChannel0);

    audioMixInputStream.setAudioMixChannels(Arrays.asList(outputChannel0, outputChannel1));
    audioMixInputStream =
        bitmovinApi.encoding.encodings.inputStreams.audioMix.create(
            encoding.getId(), audioMixInputStream);

    Stream videoStream = createStream(encoding, videoIngestInputStream, h264Config);
    Stream audioStream = createStream(encoding, audioMixInputStream, aacConfig);

    createMp4Muxing(
        encoding, output, "/", Arrays.asList(videoStream, audioStream), "stereo-track-swapped.mp4");

    executeEncoding(encoding);
  }

  /**
   * Creates an Encoding object. This is the base object to configure your encoding.
   *
   * <p>API endpoint:
   * https://bitmovin.com/docs/encoding/api-reference/sections/encodings#/Encoding/PostEncodingEncodings
   *
   * @param name A name that will help you identify the encoding, e.g. in the Bitmovin dashboard
   * @param description An optional description providing more detailed information about the
   *     encoding
   */
  private static Encoding createEncoding(String name, String description) throws BitmovinException {
    Encoding encoding = new Encoding();
    encoding.setName(name);
    encoding.setDescription(description);

    return bitmovinApi.encoding.encodings.create(encoding);
  }

  /**
   * Creates a resource representing an HTTP server providing the input files. For alternative input
   * methods see <a
   * href="https://bitmovin.com/docs/encoding/articles/supported-input-output-storages">list of
   * supported input and output storages</a>
   *
   * <p>For reasons of simplicity, a new input resource is created on each execution of this
   * example. In production use, this method should be replaced by a <a
   * href="https://bitmovin.com/docs/encoding/api-reference/sections/inputs#/Encoding/GetEncodingInputsHttpByInputId">get
   * call</a> to retrieve an existing resource.
   *
   * <p>API endpoint:
   * https://bitmovin.com/docs/encoding/api-reference/sections/inputs#/Encoding/PostEncodingInputsHttp
   *
   * @param host The hostname or IP address of the HTTP server e.g.: my-storage.biz
   */
  private static HttpInput createHttpInput(String host) throws BitmovinException {
    HttpInput input = new HttpInput();
    input.setHost(host);

    return bitmovinApi.encoding.inputs.http.create(input);
  }

  /**
   * Creates a resource representing an AWS S3 cloud storage bucket to which generated content will
   * be transferred. For alternative output methods see <a
   * href="https://bitmovin.com/docs/encoding/articles/supported-input-output-storages">list of
   * supported input and output storages</a>
   *
   * <p>The provided credentials need to allow <i>read</i>, <i>write</i> and <i>list</i> operations.
   * <i>delete</i> should also be granted to allow overwriting of existings files. See <a
   * href="https://bitmovin.com/docs/encoding/faqs/how-do-i-create-a-aws-s3-bucket-which-can-be-used-as-output-location">creating
   * an S3 bucket and setting permissions</a> for further information
   *
   * <p>For reasons of simplicity, a new output resource is created on each execution of this
   * example. In production use, this method should be replaced by a <a
   * href="https://bitmovin.com/docs/encoding/api-reference/sections/outputs#/Encoding/GetEncodingOutputsS3">get
   * call</a> retrieving an existing resource.
   *
   * <p>API endpoint:
   * https://bitmovin.com/docs/encoding/api-reference/sections/outputs#/Encoding/PostEncodingOutputsS3
   *
   * @param bucketName The name of the S3 bucket
   * @param accessKey The access key of your S3 account
   * @param secretKey The secret key of your S3 account
   */
  private static S3Output createS3Output(String bucketName, String accessKey, String secretKey)
      throws BitmovinException {

    S3Output s3Output = new S3Output();
    s3Output.setBucketName(bucketName);
    s3Output.setAccessKey(accessKey);
    s3Output.setSecretKey(secretKey);

    return bitmovinApi.encoding.outputs.s3.create(s3Output);
  }

  /**
   * Creates an IngestInputStream and adds it to an encoding
   *
   * <p>The IngestInputStream is used to define where a file to read a stream from is located
   *
   * <p>API endpoint:
   * https://bitmovin.com/docs/encoding/api-reference/sections/encodings#/Encoding/PostEncodingEncodingsInputStreamsIngestByEncodingId
   *
   * @param encoding The encoding to which the stream will be added
   * @param input The input resource providing the input file
   * @param inputPath The path to the input file
   */
  private static IngestInputStream createIngestInputStream(
      Encoding encoding, Input input, String inputPath) throws BitmovinException {
    IngestInputStream ingestInputStream = new IngestInputStream();
    ingestInputStream.setInputId(input.getId());
    ingestInputStream.setInputPath(inputPath);
    ingestInputStream.setSelectionMode(StreamSelectionMode.AUTO);

    return bitmovinApi.encoding.encodings.inputStreams.ingest.create(
        encoding.getId(), ingestInputStream);
  }

  /**
   * Adds a video or audio stream to an encoding, by mapping a codec configuration to an input
   * stream
   *
   * <p>API endpoint:
   * https://bitmovin.com/docs/encoding/api-reference/sections/encodings#/Encoding/PostEncodingEncodingsStreamsByEncodingId
   *
   * @param encoding The encoding to which the stream will be added
   * @param inputStream The inputStream resource providing the input file
   * @param codecConfiguration The codec configuration to be applied to the stream
   */
  private static Stream createStream(
      Encoding encoding, InputStream inputStream, CodecConfiguration codecConfiguration)
      throws BitmovinException {
    StreamInput streamInput = new StreamInput();
    streamInput.setInputStreamId(inputStream.getId());

    Stream stream = new Stream();
    stream.addInputStreamsItem(streamInput);
    stream.setCodecConfigId(codecConfiguration.getId());
    stream.setMode(StreamMode.STANDARD);

    return bitmovinApi.encoding.encodings.streams.create(encoding.getId(), stream);
  }

  /**
   * Creates a configuration for the H.264 video codec to be applied to video streams.
   *
   * <p>The output resolution is defined by setting the height to 1080 pixels. Width will be
   * determined automatically to maintain the aspect ratio of your input video.
   *
   * <p>To keep things simple, we use a quality-optimized VoD preset configuration, which will apply
   * proven settings for the codec. See <a
   * href="https://bitmovin.com/docs/encoding/tutorials/how-to-optimize-your-h264-codec-configuration-for-different-use-cases">How
   * to optimize your H264 codec configuration for different use-cases</a> for alternative presets.
   *
   * <p>API endpoint:
   * https://bitmovin.com/docs/encoding/api-reference/sections/configurations#/Encoding/PostEncodingConfigurationsVideoH264
   */
  private static H264VideoConfiguration createH264VideoConfig() throws BitmovinException {
    H264VideoConfiguration config = new H264VideoConfiguration();
    config.setName("H.264 1080p 1.5 Mbit/s");
    config.setPresetConfiguration(PresetConfiguration.VOD_STANDARD);
    config.setHeight(1080);
    config.setBitrate(1_500_000L);

    return bitmovinApi.encoding.configurations.video.h264.create(config);
  }

  /**
   * Creates a configuration for the AAC audio codec to be applied to audio streams.
   *
   * <p>API endpoint:
   * https://bitmovin.com/docs/encoding/api-reference/sections/configurations#/Encoding/PostEncodingConfigurationsAudioAac
   */
  private static AacAudioConfiguration createAacStereoAudioConfig() throws BitmovinException {
    AacAudioConfiguration config = new AacAudioConfiguration();
    config.setName("AAC 128 kbit/s");
    config.setBitrate(128_000L);

    return bitmovinApi.encoding.configurations.audio.aac.create(config);
  }

  /**
   * Creates an MP4 muxing.
   *
   * <p>API endpoint:
   * https://bitmovin.com/docs/encoding/api-reference/sections/encodings#/Encoding/PostEncodingEncodingsMuxingsMp4ByEncodingId
   *
   * @param encoding The encoding to add the MP4 muxing to
   * @param output The output that should be used for the muxing to write the segments to
   * @param outputPath The output path where the fragments will be written to
   * @param streams A list of streams to be added to the muxing
   * @param fileName The name of the file that will be written to the output
   */
  private static Mp4Muxing createMp4Muxing(
      Encoding encoding, Output output, String outputPath, List<Stream> streams, String fileName)
      throws BitmovinException {
    Mp4Muxing muxing = new Mp4Muxing();
    muxing.addOutputsItem(buildEncodingOutput(output, outputPath));
    muxing.setFilename(fileName);

    for (Stream stream : streams) {
      MuxingStream muxingStream = new MuxingStream();
      muxingStream.setStreamId(stream.getId());
      muxing.addStreamsItem(muxingStream);
    }

    return bitmovinApi.encoding.encodings.muxings.mp4.create(encoding.getId(), muxing);
  }

  /**
   * Builds an EncodingOutput object which defines where the output content (e.g. of a muxing) will
   * be written to. Public read permissions will be set for the files written, so they can be
   * accessed easily via HTTP.
   *
   * @param output The output resource to be used by the EncodingOutput
   * @param outputPath The path where the content will be written to
   */
  private static EncodingOutput buildEncodingOutput(Output output, String outputPath) {
    AclEntry aclEntry = new AclEntry();
    aclEntry.setPermission(AclPermission.PUBLIC_READ);

    EncodingOutput encodingOutput = new EncodingOutput();
    encodingOutput.setOutputPath(buildAbsolutePath(outputPath));
    encodingOutput.setOutputId(output.getId());
    encodingOutput.addAclItem(aclEntry);
    return encodingOutput;
  }

  /**
   * Builds an absolute path by concatenating the S3_OUTPUT_BASE_PATH configuration parameter, the
   * name of this example class and the given relative path
   *
   * <p>e.g.: /s3/base/path/ClassName/relative/path
   *
   * @param relativePath The relative path that is concatenated
   * @return The absolute path
   */
  public static String buildAbsolutePath(String relativePath) {
    String className = AudioChannelManipulation_3_ChannelSwapping.class.getSimpleName();
    return Paths.get(configProvider.getS3OutputBasePath(), className, relativePath).toString();
  }

  /**
   * Starts the actual encoding process and periodically polls its status until it reaches a final
   * state
   *
   * <p>API endpoints:
   * https://bitmovin.com/docs/encoding/api-reference/all#/Encoding/PostEncodingEncodingsStartByEncodingId
   * https://bitmovin.com/docs/encoding/api-reference/sections/encodings#/Encoding/GetEncodingEncodingsStatusByEncodingId
   *
   * <p>Please note that you can also use our webhooks API instead of polling the status. For more
   * information consult the API spec:
   * https://bitmovin.com/docs/encoding/api-reference/sections/notifications-webhooks
   *
   * @param encoding The encoding to be started
   */
  private static void executeEncoding(Encoding encoding)
      throws InterruptedException, BitmovinException {
    bitmovinApi.encoding.encodings.start(encoding.getId(), new StartEncodingRequest());

    Task task;
    do {
      Thread.sleep(5000);
      task = bitmovinApi.encoding.encodings.status(encoding.getId());
      logger.info("Encoding status is {} (progress: {} %)", task.getStatus(), task.getProgress());
    } while (task.getStatus() != Status.FINISHED
        && task.getStatus() != Status.ERROR
        && task.getStatus() != Status.CANCELED);

    if (task.getStatus() == Status.ERROR) {
      logTaskErrors(task);
      throw new RuntimeException("Encoding failed");
    }
    logger.info("Encoding finished successfully");
  }

  private static void logTaskErrors(Task task) {
    task.getMessages().stream()
        .filter(msg -> msg.getType() == MessageType.ERROR)
        .forEach(msg -> logger.error(msg.getText()));
  }
}
