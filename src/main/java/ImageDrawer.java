import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.*;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;

/**
 * JavaFX application to show a neural network learning to draw an image.
 * Created by Robert on 11/12/2016.
 */
public class ImageDrawer extends Application {

    private Image OriginalImage; //The source image displayed on the left.
    private WritableImage composition; // Destination image generated by the NN.
    private MultiLayerNetwork nn; // THE nn.
    private DataSet ds; //Training data generated (only once) from the Original, used to train.
    private INDArray xyOut; //x,y grid to calculate the output image. Needs to be calculated once, then re-used.

    /**
     * Training the NN and updating the current graphical output.
     */
    private void onCalc(){
        nn.fit(ds);
        DrawImage();
        Platform.runLater(this::onCalc);
    }

    @Override
    public void init(){
        OriginalImage = new Image("Mona_Lisa.png"); //"BlackSquare.png" is a simpler test image.

        final int w = (int) OriginalImage.getWidth();
        final int h = (int) OriginalImage.getHeight();
        composition = new WritableImage(w, h); //Right image.

        ds = generateDataSet(OriginalImage);
        nn = CreateNN();

        // The x,y grid to calculate the NN output only needs to be calculated once.
        int numPoints = h * w;
        xyOut = Nd4j.zeros(numPoints, 2);
        for (int i = 0; i < w; i++) {
            double xp = (double) i / (double) (w - 1);
            for (int j = 0; j < h; j++) {
                int index = i + w * j;
                double yp = (double) j / (double) (h - 1);

                xyOut.put(index, 0, xp); //2 inputs. x and y.
                xyOut.put(index, 1, yp);
            }
        }
        DrawImage();
    }
    /**
     * Standard JavaFX start: Build the UI, display
     */
    @Override
    public void start(Stage primaryStage) {

        final int w = (int) OriginalImage.getWidth();
        final int h = (int) OriginalImage.getHeight();
        final int zoom = 5; // Our images are a tad small, display them enlarged to have something to look at.

        ImageView iv1 = new ImageView(); //Left image
        iv1.setImage(OriginalImage);
        iv1.setFitHeight( zoom* h);
        iv1.setFitWidth(zoom*w);

        ImageView iv2 = new ImageView();
        iv2.setImage(composition);
        iv2.setFitHeight( zoom* h);
        iv2.setFitWidth(zoom*w);

        HBox root = new HBox(); //build the scene.
        Scene scene = new Scene(root);
        root.getChildren().addAll(iv1, iv2);

        primaryStage.setTitle("Neural Network Drawing Demo.");
        primaryStage.setScene(scene);
        primaryStage.show();

        Platform.setImplicitExit(true);

        //Allow JavaFX do to it's thing, Initialize the Neural network when it feels like it.
        Platform.runLater(this::onCalc);
    }

    public static void main( String[] args )
    {
        launch(args);
    }

    /**
     * Build the Neural network.
     */
    private static MultiLayerNetwork CreateNN() {
        int seed = 2345;
        int iterations = 25; //<-- Just the one iteration per call to fit.
        double learningRate = 0.1;
        int numInputs = 2;   // x and y.
        int numHiddenNodes = 25;
        int numOutputs = 3 ; //R, G and B value.

        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .seed(seed)
                .iterations(iterations)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .learningRate(learningRate)
                .weightInit(WeightInit.XAVIER)
                .updater(Updater.NESTEROVS).momentum(0.9)
                .list()
                .layer(0, new DenseLayer.Builder().nIn(numInputs).nOut(numHiddenNodes)
                        .activation("identity")
                        .build())
                .layer(1, new DenseLayer.Builder().nIn(numHiddenNodes).nOut(numHiddenNodes)
                        .activation("relu")
                        .build())
                .layer(2, new DenseLayer.Builder().nIn(numHiddenNodes).nOut(numHiddenNodes)
                        .activation("relu")
                        .build())
                .layer(3, new DenseLayer.Builder().nIn(numHiddenNodes).nOut(numHiddenNodes)
                        .activation("relu")
                        .build())
                .layer(4, new OutputLayer.Builder(LossFunctions.LossFunction.L2)
                        .activation("identity")
                        .nIn(numHiddenNodes).nOut(numOutputs).build())
                .pretrain(false).backprop(true).build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();

        return net;
    }

    /**
     * Process a javafx Image to be consumed by DeepLearning4J
     *
     * @param img Javafx image to process
     * @return DeepLearning4J DataSet.
     */
    private static DataSet generateDataSet(Image img) {
        int w = (int) img.getWidth();
        int h = (int) img.getHeight();
        int numPoints = h * w;

        PixelReader reader = img.getPixelReader();

        INDArray xy = Nd4j.zeros(numPoints, 2);
        INDArray out = Nd4j.zeros(numPoints, 3);

        //Simplest implementation first.
        for (int i = 0; i < w; i++) {
            double xp = (double) i / (double) (w - 1);
            for (int j = 0; j < h; j++) {
                Color c = reader.getColor(i, j);
                int index = i + w * j;
                double yp = (double) j / (double) (h - 1);

                xy.put(index, 0, xp); //2 inputs. x and y.
                xy.put(index, 1, yp);

                out.put(index, 0, c.getRed());  //3 outputs. the RGB values.
                out.put(index, 1, c.getGreen());
                out.put(index, 2, c.getBlue());
            }
        }
        return new DataSet(xy, out);
    }

    /**
     * Make the Neural network draw the image.
     */
    private void DrawImage() {
        int w = (int) composition.getWidth();
        int h = (int) composition.getHeight();

        INDArray out = nn.output(xyOut);
        PixelWriter writer = composition.getPixelWriter();

        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                int index = i + w * j;
                double red = CapNNOutput(out.getDouble(index, 0));
                double green = CapNNOutput(out.getDouble(index, 1));
                double blue = CapNNOutput(out.getDouble(index, 2));

                Color c = new Color(red, green, blue, 1.0);
                writer.setColor(i, j, c);
            }
        }
    }

    /**
     * Make sure the color values are >=0 and <=1
     */
    private static double CapNNOutput(double x) {
        double tmp = (x<0.0) ? 0.0 : x;
        return (tmp > 1.0) ? 1.0 : tmp;
    }
}
