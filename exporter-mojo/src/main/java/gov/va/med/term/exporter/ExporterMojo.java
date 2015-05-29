package gov.va.med.term.exporter;

import gov.va.oia.terminology.converters.sharedUtils.ConsoleUtil;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.ihtsdo.etypes.EConcept;
import org.ihtsdo.tk.dto.concept.component.description.TkDescription;
import org.ihtsdo.tk.dto.concept.component.identifier.TkIdentifier;

import au.com.bytecode.opencsv.CSVWriter;

/**
 * Goal which converts jbin data from the workbench into TSV format
 */
@Mojo( name = "export-terminology", defaultPhase = LifecyclePhase.PROCESS_SOURCES )
public class ExporterMojo extends AbstractMojo
{
	/**
	 * Where to put the output file.
	 */
	@Parameter( required = true, defaultValue = "${project.build.directory}" )
	private File outputDirectory;

	/**
	 * Location of jbin data file. Expected to be a directory.
	 */
	@Parameter (required = true)
	private File inputFile;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		try
		{
			if (!outputDirectory.exists())
			{
				outputDirectory.mkdirs();
			}

			ConsoleUtil.println("Exporting jbin concepts file");

			// Read in the jbin data
			for (File f : inputFile.listFiles(new FilenameFilter()
			{
				@Override
				public boolean accept(File dir, String name)
				{
					if (name.toLowerCase().endsWith(".jbin"))
					{
						return true;
					}
					return false;
				}
			}))
			{
				DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)));

				File outputFile = new File(outputDirectory, f.getName().substring(0, f.getName().length() - 5) +  "-export.tsv");
				CSVWriter outputFileWriter = new CSVWriter(new BufferedWriter(new FileWriter(outputFile)), '\t');
				outputFileWriter.writeNext(new String[] { "Concept ID", "Description", "Concept UUID" });

				int conceptCount = 0;
				int rowCount = 0;

				while (in.available() > 0)
				{
					EConcept temp = new EConcept(in);
					conceptCount++;

					if (conceptCount % 500 == 0)
					{
						ConsoleUtil.showProgress();
					}

					String conceptId = null;
					String conceptUUID = null;

					if (temp.getConceptAttributes() != null && temp.getConceptAttributes().getAdditionalIdComponents() != null)
					{
						for (TkIdentifier conceptIds : temp.getConceptAttributes().getAdditionalIdComponents())
						{
							if (conceptId == null)
							{
								// Just pick the first
								conceptId = conceptIds.getDenotation().toString();
							}
							//For NDFRT - prefer the N000.... ID style
							if (conceptIds.getAuthorityUuid().toString().equals("6eba5b19-acdf-54f3-9064-dc3bec1d634b"))
							{
								conceptId = conceptIds.getDenotation().toString();
								break;
							}
							//For SCT - prefer SCTIDs
							if (conceptIds.getAuthorityUuid().toString().equals("0418a591-f75b-39ad-be2c-3ab849326da9"))
							{
								conceptId = conceptIds.getDenotation().toString();
								break;
							}
						}
					}
					if (conceptId == null)
					{
						// Use the UUID
						conceptId = temp.getPrimordialUuid().toString();
					}
					conceptUUID = temp.getPrimordialUuid().toString();

					boolean hasDescriptions = false;
					if (temp.getDescriptions() != null)
					{
						for (TkDescription d : temp.getDescriptions())
						{
							hasDescriptions = true;
							rowCount++;
							outputFileWriter.writeNext(new String[] { conceptId, d.getText(), conceptUUID });
						}
					}
					if (!hasDescriptions)
					{
						outputFileWriter.writeNext(new String[] { conceptId, "", conceptUUID });
					}
				}
				in.close();
				outputFileWriter.close();
				ConsoleUtil.println("Exported " + conceptCount + " concepts from the jbin file with a total of " + rowCount + " descriptions");
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			throw new MojoExecutionException("oops", e);
		}
	}

	public static void main(String[] args) throws MojoExecutionException, MojoFailureException
	{
		ExporterMojo i = new ExporterMojo();
		i.outputDirectory = new File("../exporter-export/target");
		i.inputFile = new File("../exporter-export/target/generated-resources/data");
		i.execute();

	}
}
